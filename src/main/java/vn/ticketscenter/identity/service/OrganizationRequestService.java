package vn.ticketscenter.identity.service;

import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.dto.OrganizationRequestCreateDto;
import vn.ticketscenter.identity.dto.OrganizationRequestDetailDto;
import vn.ticketscenter.identity.dto.OrganizationRequestSummaryDto;
import vn.ticketscenter.identity.error.IdentityError;
import vn.ticketscenter.identity.error.IdentityException;
import vn.ticketscenter.identity.model.Organization;
import vn.ticketscenter.identity.model.User;
import vn.ticketscenter.identity.model.UserStatus;
import vn.ticketscenter.identity.dao.OrganizationDecisionRepository;
import vn.ticketscenter.identity.dao.OrganizationRepository;
import vn.ticketscenter.identity.dao.UserRepository;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** What a requester can do with organisation requests: submit one and read their own. */
public class OrganizationRequestService {

    private final TransactionManager transactions;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final OrganizationDecisionRepository decisions;
    private final OrganizationRequestIdempotency idempotency;

    public OrganizationRequestService(TransactionManager transactions,
                                      OrganizationRepository organizationRepository,
                                      UserRepository userRepository,
                                      OrganizationDecisionRepository decisions,
                                      OrganizationRequestIdempotency idempotency) {
        this.transactions = Objects.requireNonNull(transactions, "transactions must not be null.");
        this.organizationRepository =
                Objects.requireNonNull(organizationRepository, "organizationRepository must not be null.");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null.");
        this.decisions = Objects.requireNonNull(decisions, "decisions must not be null.");
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency must not be null.");
    }

    public OrganizationRequestService(TransactionManager transactions,
                                      OrganizationRepository organizationRepository,
                                      UserRepository userRepository,
                                      OrganizationDecisionRepository decisions) {
        this(transactions, organizationRepository, userRepository, decisions,
                OrganizationRequestIdempotency.none());
    }

    public OrganizationRequestService(TransactionManager transactions,
                                      OrganizationRepository organizationRepository,
                                      UserRepository userRepository) {
        this(transactions, organizationRepository, userRepository, new OrganizationDecisionRepository());
    }

    /**
     * Submits a new organisation request. When an idempotency key is given, a retry with the same payload
     * returns the first result and a retry with a different payload is rejected.
     */
    public OrganizationRequestDetailDto submitRequest(UUID actorId, OrganizationRequestCreateDto dto,
                                                      String idempotencyKey) {
        if (actorId == null) {
            throw new IllegalArgumentException("actorId must not be null.");
        }
        if (dto == null) {
            throw new IllegalArgumentException("dto must not be null.");
        }
        dto.validate();

        String payloadHash = OrganizationRequestIdempotency.payloadHash(dto);
        String cacheKey = idempotency.keyFor(actorId, idempotencyKey);
        if (cacheKey != null) {
            Optional<OrganizationRequestDetailDto> replay = idempotency.find(cacheKey, payloadHash);
            if (replay.isPresent()) {
                return replay.get();
            }
        }

        requireCanSubmit(actorId);
        UUID organizationId = decisions.submit(transactions, actorId, dto.name().trim(),
                dto.contactEmail().trim(), trimmedOrNull(dto.contactPhone()), EventScheduleTag.appendTo(dto));
        OrganizationRequestDetailDto result = transactions.execute(DatabasePrincipal.AUTH, em ->
                OrganizationRequestDetailDto.from(organizationRepository.findById(em, organizationId)
                        .orElseThrow(() -> new NoSuchElementException(
                                "ORGANIZATION_NOT_FOUND: Không tìm thấy hồ sơ yêu cầu"))));

        if (cacheKey != null) {
            idempotency.remember(cacheKey, payloadHash, result);
        }
        return result;
    }

    /** Fails fast, before the stored procedure runs, for a suspended user or one with a pending request. */
    private void requireCanSubmit(UUID actorId) {
        transactions.execute(DatabasePrincipal.AUTH, em -> {
            User user = userRepository.findById(em, actorId)
                    .orElseThrow(() -> new IllegalArgumentException("USER_NOT_FOUND: Người dùng không tồn tại"));
            if (user.getStatus() != UserStatus.ACTIVE) {
                throw new IdentityException(IdentityError.USER_SUSPENDED,
                        "Tài khoản của bạn hiện không thể gửi yêu cầu");
            }
            if (organizationRepository.hasPendingRequest(em, actorId)) {
                throw new IdentityException(IdentityError.ORGANIZATION_REQUEST_EXISTS,
                        "Bạn đang có một yêu cầu tạo tổ chức đang chờ xét duyệt");
            }
            return null;
        });
    }

    /** All requests the actor has submitted. */
    public List<OrganizationRequestSummaryDto> getMyRequests(UUID actorId) {
        if (actorId == null) {
            throw new IllegalArgumentException("actorId must not be null.");
        }
        return transactions.execute(DatabasePrincipal.AUTH, em -> {
            List<Organization> requests = organizationRepository.findByRequesterId(em, actorId);
            return requests == null ? List.<OrganizationRequestSummaryDto>of()
                    : requests.stream().map(OrganizationRequestSummaryDto::from).toList();
        });
    }

    /**
     * One request's detail. Anyone but an admin may only read a request they submitted.
     *
     * @throws SecurityException when a non-admin asks for someone else's request
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
                    .orElseThrow(() -> new NoSuchElementException(
                            "ORGANIZATION_NOT_FOUND: Không tìm thấy hồ sơ yêu cầu"));
            if (!isAdmin && (org.getRequester() == null || !org.getRequester().getId().equals(actorId))) {
                throw new SecurityException("FORBIDDEN: Bạn không có quyền truy cập hồ sơ này");
            }
            return OrganizationRequestDetailDto.from(org);
        });
    }

    private static String trimmedOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
