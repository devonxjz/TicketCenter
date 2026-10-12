package vn.ticketscenter.identity.service;

import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.config.web.JsonObjectParser;
import vn.ticketscenter.event.model.Event;
import vn.ticketscenter.event.model.EventCategory;
import vn.ticketscenter.identity.dto.OrganizationRequestCreateDto;
import vn.ticketscenter.identity.dto.OrganizationRequestDetailDto;
import vn.ticketscenter.identity.dto.OrganizationRequestSummaryDto;
import vn.ticketscenter.identity.model.Organization;
import vn.ticketscenter.identity.model.OrganizationMembership;
import vn.ticketscenter.identity.model.OrganizationRole;
import vn.ticketscenter.identity.model.OrganizationStatus;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.model.User;
import vn.ticketscenter.identity.model.UserStatus;
import vn.ticketscenter.identity.repository.OrganizationRepository;
import vn.ticketscenter.identity.repository.UserRepository;

import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service handling organization creation requests and lifecycle operations.
 */
public class OrganizationRequestService {

    private static final Logger LOGGER = Logger.getLogger(OrganizationRequestService.class.getName());
    private static final int IDEMPOTENCY_TTL_SECONDS = 86400;

    interface RedisStore {
        String get(String key);
        void setex(String key, int seconds, String value);
    }

    private final TransactionManager transactions;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final RedisClientProvider redisProvider;
    private final RedisStore redisStore;

    private OrganizationRequestService(TransactionManager transactions,
                                      OrganizationRepository organizationRepository,
                                      UserRepository userRepository,
                                      RedisClientProvider redisProvider,
                                      RedisStore redisStore) {
        this.transactions = Objects.requireNonNull(transactions, "transactions must not be null.");
        this.organizationRepository = Objects.requireNonNull(organizationRepository, "organizationRepository must not be null.");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null.");
        this.redisProvider = redisProvider;
        this.redisStore = redisStore;
    }

    public OrganizationRequestService(TransactionManager transactions,
                                      OrganizationRepository organizationRepository,
                                      UserRepository userRepository) {
        this(transactions, organizationRepository, userRepository, null, null);
    }

    public OrganizationRequestService(TransactionManager transactions,
                                      OrganizationRepository organizationRepository,
                                      UserRepository userRepository,
                                      RedisClientProvider redisProvider) {
        this(transactions, organizationRepository, userRepository, redisProvider,
                redisProvider != null ? new RedisStore() {
                    @Override
                    public String get(String key) {
                        try (var jedis = redisProvider.getResource()) {
                            return jedis.get(key);
                        }
                    }

                    @Override
                    public void setex(String key, int seconds, String value) {
                        try (var jedis = redisProvider.getResource()) {
                            jedis.setex(key, seconds, value);
                        }
                    }
                } : null);
    }

    static OrganizationRequestService withRedisStore(TransactionManager transactions,
                                                     OrganizationRepository organizationRepository,
                                                     UserRepository userRepository,
                                                     RedisStore redisStore) {
        return new OrganizationRequestService(transactions, organizationRepository, userRepository, null, redisStore);
    }

    /**
     * Submits a new organization approval request.
     * Enforces idempotency when an idempotency key is provided.
     */
    public OrganizationRequestDetailDto submitRequest(UUID actorId, OrganizationRequestCreateDto dto, String idempotencyKey) {
        if (actorId == null) {
            throw new IllegalArgumentException("actorId must not be null.");
        }
        if (dto == null) {
            throw new IllegalArgumentException("dto must not be null.");
        }
        dto.validate();

        String payloadHash = computePayloadHash(dto);
        String redisKey = null;

        if (idempotencyKey != null && !idempotencyKey.isBlank() && redisStore != null) {
            redisKey = "idempotency:org-request:" + actorId + ":" + idempotencyKey.trim();
            try {
                String cached = redisStore.get(redisKey);
                if (cached != null && cached.startsWith("{")) {
                    Map<String, String> map = JsonObjectParser.parse(new StringReader(cached));
                    String cachedHash = map.get("payloadHash");
                    if (cachedHash != null && !cachedHash.equals(payloadHash)) {
                        throw new IllegalStateException("IDEMPOTENCY_CONFLICT: Idempotency-Key đã được sử dụng với nội dung khác");
                    }
                    OrganizationRequestDetailDto cachedDto = deserializeDetail(map);
                    if (cachedDto != null) {
                        return cachedDto;
                    }
                }
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to read idempotency cache: " + e.getMessage());
            }
        }

        OrganizationRequestDetailDto result = transactions.execute(DatabasePrincipal.AUTH, em -> {
            User user = userRepository.findById(em, actorId)
                    .orElseThrow(() -> new IllegalArgumentException("USER_NOT_FOUND: Người dùng không tồn tại"));

            if (user.getStatus() != UserStatus.ACTIVE) {
                throw new IllegalStateException("USER_SUSPENDED: Tài khoản của bạn hiện không thể gửi yêu cầu");
            }

            if (organizationRepository.hasPendingRequest(em, actorId)) {
                throw new IllegalStateException("ORGANIZATION_REQUEST_EXISTS: Bạn đang có một yêu cầu tạo tổ chức đang chờ xét duyệt");
            }

            String contactPhone = (dto.contactPhone() != null && !dto.contactPhone().isBlank())
                    ? dto.contactPhone().trim()
                    : null;
            String description = (dto.description() != null && !dto.description().isBlank())
                    ? dto.description().trim()
                    : null;

            StringBuilder scheduleTag = new StringBuilder();
            if (dto.eventTitle() != null && !dto.eventTitle().isBlank()) {
                scheduleTag.append("[Event: ").append(dto.eventTitle().trim());
                if (dto.eventDate() != null && !dto.eventDate().isBlank()) {
                    scheduleTag.append(" | Date: ").append(dto.eventDate().trim());
                }
                if (dto.eventStartTime() != null && !dto.eventStartTime().isBlank()) {
                    scheduleTag.append(" | Start: ").append(dto.eventStartTime().trim());
                }
                if (dto.eventEndTime() != null && !dto.eventEndTime().isBlank()) {
                    scheduleTag.append(" | End: ").append(dto.eventEndTime().trim());
                }
                if (dto.eventVenue() != null && !dto.eventVenue().isBlank()) {
                    scheduleTag.append(" | Venue: ").append(dto.eventVenue().trim());
                }
                scheduleTag.append("]");
            }

            if (scheduleTag.length() > 0) {
                String tag = scheduleTag.toString();
                if (description == null) {
                    description = tag;
                } else if (!description.contains("[Event:")) {
                    description = description + "\n" + tag;
                }
                if (description.length() > 2000) {
                    description = description.substring(0, 2000);
                }
            }

            Organization org = new Organization(
                    dto.name().trim(),
                    dto.contactEmail().trim(),
                    contactPhone,
                    description,
                    user
            );
            org.submitForApproval();
            Organization saved = organizationRepository.save(em, org);
            return OrganizationRequestDetailDto.from(saved);
        });

        if (redisKey != null && redisStore != null) {
            try {
                redisStore.setex(redisKey, IDEMPOTENCY_TTL_SECONDS, serializeCacheEntry(payloadHash, result));
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to cache idempotency result: " + e.getMessage());
            }
        }

        return result;
    }

    /**
     * Lists all organization requests submitted by the actor.
     */
    public List<OrganizationRequestSummaryDto> getMyRequests(UUID actorId) {
        if (actorId == null) {
            throw new IllegalArgumentException("actorId must not be null.");
        }
        return transactions.execute(DatabasePrincipal.AUTH, em -> {
            List<Organization> list = organizationRepository.findByRequesterId(em, actorId);
            if (list == null) {
                return List.of();
            }
            return list.stream().map(OrganizationRequestSummaryDto::from).toList();
        });
    }

    /**
     * Lists all pending organization requests for an admin.
     */
    public List<OrganizationRequestSummaryDto> getPendingRequestsForAdmin(UUID adminId) {
        if (adminId == null) {
            throw new IllegalArgumentException("adminId must not be null.");
        }
        return transactions.execute(DatabasePrincipal.AUTH, em -> {
            User user = userRepository.findById(em, adminId)
                    .orElseThrow(() -> new IllegalArgumentException("USER_NOT_FOUND: Người dùng không tồn tại"));
            if (user.getPlatformRole() != PlatformRole.ADMIN) {
                throw new SecurityException("FORBIDDEN: Chỉ quản trị viên mới có quyền xem danh sách yêu cầu");
            }
            List<Organization> list = organizationRepository.findPending(em);
            if (list == null) {
                return List.of();
            }
            return list.stream().map(OrganizationRequestSummaryDto::from).toList();
        });
    }

    /**
     * Counts pending organization requests for an admin.
     */
    public long countPendingRequestsForAdmin(UUID adminId) {
        if (adminId == null) {
            throw new IllegalArgumentException("adminId must not be null.");
        }
        return transactions.execute(DatabasePrincipal.AUTH, em -> {
            User user = userRepository.findById(em, adminId)
                    .orElseThrow(() -> new IllegalArgumentException("USER_NOT_FOUND: Người dùng không tồn tại"));
            if (user.getPlatformRole() != PlatformRole.ADMIN) {
                throw new SecurityException("FORBIDDEN: Chỉ quản trị viên mới có quyền xem danh sách yêu cầu");
            }
            return organizationRepository.countPending(em);
        });
    }

    public List<OrganizationRequestSummaryDto> getRequestsForAdmin(UUID adminId, OrganizationStatus status, int page, int pageSize) {
        if (adminId == null) {
            throw new IllegalArgumentException("adminId must not be null.");
        }
        return transactions.execute(DatabasePrincipal.AUTH, em -> {
            User user = userRepository.findById(em, adminId)
                    .orElseThrow(() -> new IllegalArgumentException("USER_NOT_FOUND: Người dùng không tồn tại"));
            if (user.getPlatformRole() != PlatformRole.ADMIN) {
                throw new SecurityException("FORBIDDEN: Chỉ quản trị viên mới có quyền xem danh sách yêu cầu");
            }
            int p = Math.max(1, page);
            int size = Math.max(1, pageSize);
            int offset = (p - 1) * size;
            List<Organization> list = organizationRepository.findByStatus(em, status, offset, size);
            if (list == null) {
                return List.of();
            }
            return list.stream().map(OrganizationRequestSummaryDto::from).toList();
        });
    }

    public long countRequestsForAdmin(UUID adminId, OrganizationStatus status) {
        if (adminId == null) {
            throw new IllegalArgumentException("adminId must not be null.");
        }
        return transactions.execute(DatabasePrincipal.AUTH, em -> {
            User user = userRepository.findById(em, adminId)
                    .orElseThrow(() -> new IllegalArgumentException("USER_NOT_FOUND: Người dùng không tồn tại"));
            if (user.getPlatformRole() != PlatformRole.ADMIN) {
                throw new SecurityException("FORBIDDEN: Chỉ quản trị viên mới có quyền xem danh sách yêu cầu");
            }
            return organizationRepository.countByStatus(em, status);
        });
    }

    /**
     * Approves an organization request, promotes the requester to manager,
     * and sets up the initial event for the organization.
     */
    public OrganizationRequestDetailDto approveRequest(UUID adminId, UUID organizationId) {
        return approveRequest(adminId, organizationId, null, null, null);
    }

    public OrganizationRequestDetailDto approveRequest(UUID adminId, UUID organizationId,
                                                      BigDecimal ratePercent, Long fixedFee,
                                                      Instant effectiveFrom) {
        return approveRequest(adminId, organizationId, ratePercent, fixedFee, effectiveFrom, null);
    }

    public OrganizationRequestDetailDto approveRequest(UUID adminId, UUID organizationId,
                                                      BigDecimal ratePercent, Long fixedFee,
                                                      Instant effectiveFrom, Instant effectiveTo) {
        if (adminId == null) {
            throw new IllegalArgumentException("adminId must not be null.");
        }
        if (organizationId == null) {
            throw new IllegalArgumentException("organizationId must not be null.");
        }
        return transactions.execute(DatabasePrincipal.AUTH, em -> {
            User admin = userRepository.findById(em, adminId)
                    .orElseThrow(() -> new IllegalArgumentException("USER_NOT_FOUND: Người dùng không tồn tại"));
            if (admin.getPlatformRole() != PlatformRole.ADMIN) {
                throw new SecurityException("FORBIDDEN: Chỉ quản trị viên mới có quyền phê duyệt yêu cầu");
            }
            Organization org = organizationRepository.findById(em, organizationId)
                    .orElseThrow(() -> new NoSuchElementException("ORGANIZATION_NOT_FOUND: Không tìm thấy hồ sơ yêu cầu"));
            if (org.getStatus() != OrganizationStatus.PENDING_APPROVAL) {
                throw new IllegalStateException("CONFLICT: Yêu cầu không ở trạng thái PENDING_APPROVAL");
            }
            org.approve();

            User requester = org.getRequester();
            if (requester != null) {
                OrganizationMembership membership = new OrganizationMembership(requester, org, OrganizationRole.MANAGER);
                em.persist(membership);
            }

            vn.ticketscenter.settlement.model.CommissionRule rule = null;
            if (ratePercent != null && fixedFee != null) {
                Instant from = effectiveFrom != null ? effectiveFrom : Instant.now();
                Instant to = effectiveTo != null ? effectiveTo : from.plus(java.time.Duration.ofDays(3650));
                rule = new vn.ticketscenter.settlement.model.CommissionRule(org, ratePercent, fixedFee, from, to);
                em.persist(rule);
            }

            Instant startTime = Instant.now().plus(java.time.Duration.ofDays(14));
            Instant endTime = startTime.plus(java.time.Duration.ofHours(3));
            String title = "Sự kiện khởi tạo - " + org.getName();
            String venue = "Trung tâm Hội nghị Quốc gia";

            String desc = org.getDescription();
            if (desc != null && desc.contains("[Event: ")) {
                int startIdx = desc.indexOf("[Event: ") + 8;
                int endIdx = desc.indexOf("]", startIdx);
                String eventTag = endIdx != -1 ? desc.substring(startIdx, endIdx) : desc.substring(startIdx);
                String[] parts = eventTag.split("\\|");
                String eventDateStr = null;
                String eventStartTimeStr = null;
                String eventEndTimeStr = null;
                for (String part : parts) {
                    String p = part.trim();
                    if (!p.contains(":")) {
                        if (!p.isBlank()) {
                            title = p;
                        }
                    } else {
                        String[] kv = p.split(":", 2);
                        String k = kv[0].trim().toLowerCase();
                        String v = kv[1].trim();
                        if (k.equals("venue") && !v.isBlank()) {
                            venue = v;
                        } else if (k.equals("date") && !v.isBlank()) {
                            eventDateStr = v;
                        } else if ((k.equals("start") || k.equals("starttime")) && !v.isBlank()) {
                            eventStartTimeStr = v;
                        } else if ((k.equals("end") || k.equals("endtime")) && !v.isBlank()) {
                            eventEndTimeStr = v;
                        }
                    }
                }

                if (eventDateStr != null && !eventDateStr.isBlank()) {
                    try {
                        if (eventDateStr.contains("T")) {
                            startTime = Instant.parse(eventDateStr);
                            endTime = startTime.plus(java.time.Duration.ofHours(3));
                        } else {
                            java.time.LocalDate d = java.time.LocalDate.parse(eventDateStr);
                            java.time.LocalTime startT = java.time.LocalTime.of(9, 0);
                            if (eventStartTimeStr != null && !eventStartTimeStr.isBlank()) {
                                try {
                                    startT = java.time.LocalTime.parse(eventStartTimeStr);
                                } catch (Exception ignored) {
                                }
                            }
                            startTime = d.atTime(startT).atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();

                            if (eventEndTimeStr != null && !eventEndTimeStr.isBlank()) {
                                try {
                                    java.time.LocalTime endT = java.time.LocalTime.parse(eventEndTimeStr);
                                    endTime = d.atTime(endT).atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
                                } catch (Exception ignored) {
                                    endTime = startTime.plus(java.time.Duration.ofHours(3));
                                }
                            } else {
                                endTime = startTime.plus(java.time.Duration.ofHours(3));
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }

            UUID categoryId = null;
            try {
                List<?> cats = em.createNativeQuery("SELECT TOP 1 id FROM dbo.tc_event_categories").getResultList();
                if (cats != null && !cats.isEmpty()) {
                    categoryId = UUID.fromString(cats.get(0).toString());
                } else {
                    categoryId = UUID.randomUUID();
                    em.createNativeQuery("INSERT INTO dbo.tc_event_categories (id, name, slug) VALUES (:id, N'Hòa nhạc', 'concert')")
                            .setParameter("id", categoryId).executeUpdate();
                }
            } catch (Exception e) {
                categoryId = UUID.randomUUID();
            }

            Event initialEvent = new Event(
                    org,
                    title,
                    org.getDescription() != null ? org.getDescription() : "Sự kiện được phê duyệt",
                    EventCategory.CONCERT,
                    venue,
                    venue != null ? venue : "Hà Nội",
                    startTime,
                    endTime
            );
            initialEvent.setCategoryId(categoryId);
            if (rule != null) {
                initialEvent.setCommissionRule(rule);
            }
            em.persist(initialEvent);

            Organization saved = organizationRepository.save(em, org);
            UUID ruleId = rule != null ? rule.getId() : null;
            return new OrganizationRequestDetailDto(
                    saved.getId(),
                    saved.getName(),
                    saved.getContactEmail(),
                    saved.getContactPhone(),
                    saved.getDescription(),
                    saved.getStatus(),
                    requester != null ? requester.getId() : null,
                    saved.getSubmittedAt(),
                    saved.getRejectionReason(),
                    ruleId
            );
        });
    }

    /**
     * Retrieves detail of an organization request.
     * Enforces IDOR security check for non-admin actors.
     */
    public OrganizationRequestDetailDto getRequestDetail(UUID actorId, boolean isAdmin, UUID organizationId) {
        if (actorId == null) {
            throw new IllegalArgumentException("actorId must not be null.");
        }
        if (organizationId == null) {
            throw new IllegalArgumentException("organizationId must not be null.");
        }
        return transactions.execute(DatabasePrincipal.AUTH, em -> {
            Organization org = organizationRepository.findById(em, organizationId)
                    .orElseThrow(() -> new NoSuchElementException("ORGANIZATION_NOT_FOUND: Không tìm thấy hồ sơ yêu cầu"));
            if (!isAdmin && (org.getRequester() == null || !org.getRequester().getId().equals(actorId))) {
                throw new SecurityException("FORBIDDEN: Bạn không có quyền truy cập hồ sơ này");
            }
            return OrganizationRequestDetailDto.from(org);
        });
    }

    /**
     * Rejects an organization request with a required reason.
     */
    public OrganizationRequestDetailDto rejectRequest(UUID adminId, UUID organizationId, String reason) {
        if (adminId == null) {
            throw new IllegalArgumentException("adminId must not be null.");
        }
        if (organizationId == null) {
            throw new IllegalArgumentException("organizationId must not be null.");
        }
        if (reason == null || reason.trim().length() < 10 || reason.trim().length() > 500) {
            throw new IllegalArgumentException("Lý do từ chối phải từ 10 đến 500 ký tự");
        }
        return transactions.execute(DatabasePrincipal.AUTH, em -> {
            User admin = userRepository.findById(em, adminId)
                    .orElseThrow(() -> new IllegalArgumentException("USER_NOT_FOUND: Người dùng không tồn tại"));
            if (admin.getPlatformRole() != PlatformRole.ADMIN) {
                throw new SecurityException("FORBIDDEN: Chỉ quản trị viên mới có quyền từ chối yêu cầu");
            }
            Organization org = organizationRepository.findById(em, organizationId)
                    .orElseThrow(() -> new NoSuchElementException("ORGANIZATION_NOT_FOUND: Không tìm thấy hồ sơ yêu cầu"));
            if (org.getStatus() != OrganizationStatus.PENDING_APPROVAL) {
                throw new IllegalStateException("CONFLICT: Yêu cầu không ở trạng thái PENDING_APPROVAL");
            }
            org.reject(reason.trim());
            Organization saved = organizationRepository.save(em, org);
            return OrganizationRequestDetailDto.from(saved);
        });
    }

    private static String computePayloadHash(OrganizationRequestCreateDto dto) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String normalized = String.join("\n", java.util.stream.Stream.of(
                    dto.name(), dto.contactEmail(), dto.contactPhone(), dto.description(),
                    dto.eventTitle(), dto.eventDate(), dto.eventStartTime(), dto.eventEndTime(), dto.eventVenue()
            ).map(s -> s == null ? "" : s.trim()).toList());
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private static String serializeCacheEntry(String payloadHash, OrganizationRequestDetailDto dto) {
        return "{\"payloadHash\":" + HttpResponses.jsonString(payloadHash) + "," + dto.toJson().substring(1);
    }

    private static OrganizationRequestDetailDto deserializeDetail(Map<String, String> map) {
        try {
            String orgIdStr = map.get("organizationId");
            UUID orgId = (orgIdStr != null && !orgIdStr.isBlank()) ? UUID.fromString(orgIdStr) : null;
            String name = map.get("name");
            String contactEmail = map.get("contactEmail");
            String contactPhoneStr = map.get("contactPhone");
            String contactPhone = (contactPhoneStr != null && !contactPhoneStr.isBlank()) ? contactPhoneStr : null;
            String descriptionStr = map.get("description");
            String description = (descriptionStr != null && !descriptionStr.isBlank()) ? descriptionStr : null;
            String statusStr = map.get("status");
            OrganizationStatus status = (statusStr != null && !statusStr.isBlank()) ? OrganizationStatus.valueOf(statusStr) : null;
            String reqIdStr = map.get("requesterId");
            UUID requesterId = (reqIdStr != null && !reqIdStr.isBlank()) ? UUID.fromString(reqIdStr) : null;
            String submittedAtStr = map.get("submittedAt");
            Instant submittedAt = (submittedAtStr != null && !submittedAtStr.isBlank()) ? Instant.parse(submittedAtStr) : null;
            String rejectionReasonStr = map.get("rejectionReason");
            String rejectionReason = (rejectionReasonStr != null && !rejectionReasonStr.isBlank()) ? rejectionReasonStr : null;

            return new OrganizationRequestDetailDto(orgId, name, contactEmail, contactPhone, description, status, requesterId, submittedAt, rejectionReason);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to deserialize cached detail: " + e.getMessage());
            return null;
        }
    }
}
