package vn.ticketscenter.identity.controller;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.TypedQuery;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.config.web.JsonObjectParser;
import vn.ticketscenter.event.dto.EventDto;
import vn.ticketscenter.event.model.Event;
import vn.ticketscenter.event.repository.EventRepository;
import vn.ticketscenter.event.service.EventService;
import vn.ticketscenter.identity.dto.LoginRequestDto;
import vn.ticketscenter.identity.model.Organization;
import vn.ticketscenter.identity.model.OrganizationMembership;
import vn.ticketscenter.identity.model.OrganizationRole;
import vn.ticketscenter.identity.model.OrganizationStatus;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.model.User;
import vn.ticketscenter.identity.repository.OrganizationRepository;
import vn.ticketscenter.identity.repository.UserRepository;
import vn.ticketscenter.identity.security.CsrfFilter;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.identity.service.AuthService;
import vn.ticketscenter.identity.service.OrganizationRequestService;
import vn.ticketscenter.identity.service.PasswordHasher;
import vn.ticketscenter.persistence.model.BaseEntity;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AdminOrganizationApprovalAndScheduleE2ETest {

    private Map<UUID, User> userDatabase;
    private Map<UUID, Organization> organizationDatabase;
    private List<OrganizationMembership> membershipDatabase;
    private List<Event> eventDatabase;

    private UserRepository userRepository;
    private OrganizationRepository organizationRepository;
    private TransactionManager transactionManager;
    private OrganizationRequestService organizationRequestService;
    private OrganizationRequestServlet servlet;
    private CsrfFilter csrfFilter;
    private AuthService authService;
    private EventService eventService;

    private User musicianUser;
    private User adminUser;
    private static final String DEFAULT_PASSWORD = "Password123456";
    private PasswordHasher passwordHasher;

    @BeforeEach
    void setUp() {
        userDatabase = new LinkedHashMap<>();
        organizationDatabase = new LinkedHashMap<>();
        membershipDatabase = new ArrayList<>();
        eventDatabase = new ArrayList<>();

        passwordHasher = new PasswordHasher();

        musicianUser = new User("musician@ticketscenter.vn", "musician", "Musician User", passwordHasher.hash(DEFAULT_PASSWORD));
        setEntityId(musicianUser, UUID.randomUUID());
        musicianUser.grantPlatformRole("CUSTOMER");
        userDatabase.put(musicianUser.getId(), musicianUser);

        adminUser = new User("admin@ticketscenter.vn", "admin", "System Administrator", passwordHasher.hash(DEFAULT_PASSWORD));
        setEntityId(adminUser, UUID.randomUUID());
        adminUser.grantPlatformRole("ADMIN");
        userDatabase.put(adminUser.getId(), adminUser);

        userRepository = new UserRepository() {
            @Override
            public Optional<User> findById(EntityManager em, UUID id) {
                return Optional.ofNullable(userDatabase.get(id));
            }

            @Override
            public Optional<User> findByNormalizedEmail(EntityManager em, String email) {
                return userDatabase.values().stream()
                        .filter(u -> u.getEmail().equalsIgnoreCase(email))
                        .findFirst();
            }

            @Override
            public Optional<User> findByNormalizedUserName(EntityManager em, String userName) {
                return userDatabase.values().stream()
                        .filter(u -> u.getUserName().equalsIgnoreCase(userName))
                        .findFirst();
            }
        };

        organizationRepository = new OrganizationRepository() {
            @Override
            public Optional<Organization> findById(EntityManager em, UUID id) {
                return Optional.ofNullable(organizationDatabase.get(id));
            }

            @Override
            public List<Organization> findByRequesterId(EntityManager em, UUID requesterId) {
                return organizationDatabase.values().stream()
                        .filter(o -> o.getRequester() != null && requesterId.equals(o.getRequester().getId()))
                        .toList();
            }

            @Override
            public boolean hasPendingRequest(EntityManager em, UUID requesterId) {
                return organizationDatabase.values().stream()
                        .anyMatch(o -> o.getStatus() == OrganizationStatus.PENDING_APPROVAL
                                && o.getRequester() != null
                                && requesterId.equals(o.getRequester().getId()));
            }

            @Override
            public List<Organization> findPending(EntityManager em) {
                return organizationDatabase.values().stream()
                        .filter(o -> o.getStatus() == OrganizationStatus.PENDING_APPROVAL)
                        .toList();
            }

            @Override
            public long countPending(EntityManager em) {
                return organizationDatabase.values().stream()
                        .filter(o -> o.getStatus() == OrganizationStatus.PENDING_APPROVAL)
                        .count();
            }

            @Override
            public Organization save(EntityManager em, Organization org) {
                if (org.getId() == null) {
                    setEntityId(org, UUID.randomUUID());
                }
                organizationDatabase.put(org.getId(), org);
                return org;
            }
        };

        transactionManager = createTransactionManager();
        organizationRequestService = new OrganizationRequestService(transactionManager, organizationRepository, userRepository);
        servlet = new OrganizationRequestServlet(organizationRequestService);
        csrfFilter = new CsrfFilter();
        authService = new AuthService(transactionManager, userRepository, passwordHasher);

        EventRepository eventRepository = new EventRepository((DataSource) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{DataSource.class},
                (proxy, method, args) -> null
        )) {
            @Override
            public List<EventDto> managedEvents(UserPrincipal actor, UUID orgId) {
                if (actor == null || (!actor.isAdmin() && actor.organizationRole() != OrganizationRole.MANAGER)) {
                    throw new SecurityException("EVENT_FORBIDDEN");
                }
                return eventDatabase.stream()
                        .filter(e -> e.getOrganization() != null && orgId.equals(e.getOrganization().getId()))
                        .map(e -> new EventDto(
                                e.getId(),
                                e.getOrganization().getId(),
                                UUID.randomUUID(),
                                e.getTitle(),
                                e.getDescription(),
                                e.getVenueName(),
                                e.getVenueAddress(),
                                e.getSaleStart(),
                                e.getSaleEnd(),
                                e.getStartTime(),
                                e.getEndTime(),
                                e.getStatus().name(),
                                1,
                                e.getCoverImageUrl(),
                                e.getEventImagePublicId()
                        ))
                        .toList();
            }
        };
        vn.ticketscenter.storage.StorageService storageService = (vn.ticketscenter.storage.StorageService) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{vn.ticketscenter.storage.StorageService.class},
                (proxy, method, args) -> null
        );
        eventService = new EventService(eventRepository, storageService);
    }

    @Test
    @DisplayName("End-to-End lifecycle test for organization request submission, admin notification bell, approval, and event schedule")
    void testAdminOrganizationApprovalAndScheduleE2E() throws Exception {
        // Step 1. User Submission with Auto-Email and Event Schedule
        Map<String, Object> requesterSession = new LinkedHashMap<>();
        String requesterCsrfToken = UUID.randomUUID().toString();
        requesterSession.put(SecurityContext.CSRF_TOKEN_ATTRIBUTE, requesterCsrfToken);

        UserPrincipal initialMusicianPrincipal = new UserPrincipal(
                musicianUser.getId(),
                PlatformRole.CUSTOMER,
                1,
                null,
                "musician@ticketscenter.vn"
        );
        requesterSession.put(SecurityContext.SESSION_USER_ATTRIBUTE, initialMusicianPrincipal);

        String createPayload = "{"
                + "\"name\":\"Ban Nhạc Sống Sài Gòn\","
                + "\"contactEmail\":\"musician@ticketscenter.vn\","
                + "\"contactPhone\":\"0987654321\","
                + "\"description\":\"Chương trình biểu diễn định kỳ\","
                + "\"eventTitle\":\"Live Concert Âm Hưởng Mùa Thu\","
                + "\"eventDate\":\"2026-11-20\","
                + "\"eventStartTime\":\"19:30\","
                + "\"eventEndTime\":\"22:30\","
                + "\"eventVenue\":\"Nhà Hát Thành Phố\""
                + "}";

        Map<String, String> submitHeaders = new LinkedHashMap<>();
        submitHeaders.put("X-CSRF-Token", requesterCsrfToken);

        ServletExecutionResult submitResult = executeRequest(
                "POST",
                "/api/organization-requests",
                requesterSession,
                submitHeaders,
                createPayload
        );

        assertEquals(201, submitResult.status, "Step 1 must return 201 Created.");
        assertTrue(submitResult.body.contains("Ban Nhạc Sống Sài Gòn"));
        assertTrue(submitResult.body.contains("PENDING_APPROVAL"));

        UUID organizationId = extractOrganizationId(submitResult.body);
        assertNotNull(organizationId, "Organization ID must be present in response payload.");

        // Step 2. Admin Notification Bell Detection
        Map<String, Object> adminSession = new LinkedHashMap<>();
        String adminCsrfToken = UUID.randomUUID().toString();
        adminSession.put(SecurityContext.CSRF_TOKEN_ATTRIBUTE, adminCsrfToken);

        UserPrincipal adminPrincipal = new UserPrincipal(
                adminUser.getId(),
                PlatformRole.ADMIN,
                1,
                null,
                "admin@ticketscenter.vn"
        );
        adminSession.put(SecurityContext.SESSION_USER_ATTRIBUTE, adminPrincipal);

        ServletExecutionResult pendingListResult = executeRequest(
                "GET",
                "/api/admin/organization-requests/pending",
                adminSession,
                Collections.emptyMap(),
                null
        );

        assertEquals(200, pendingListResult.status, "Step 2 must return 200 OK.");
        long pendingCount = extractCount(pendingListResult.body);
        assertTrue(pendingCount >= 1, "Pending count must be at least 1.");

        assertTrue(pendingListResult.body.contains(organizationId.toString()), "Returned list must contain organizationId.");
        assertTrue(pendingListResult.body.contains("Ban Nhạc Sống Sài Gòn"), "Returned list must contain organization name.");
        assertTrue(pendingListResult.body.contains("musician@ticketscenter.vn"), "Returned list must contain requester email.");

        boolean notificationBellRedDot = pendingCount > 0;
        assertTrue(notificationBellRedDot, "Notification bell must show red dot badge when pending count is greater than zero.");

        // Step 3. Admin Approval from Notification
        Map<String, String> approveHeaders = new LinkedHashMap<>();
        approveHeaders.put("X-CSRF-Token", adminCsrfToken);

        ServletExecutionResult approveResult = executeRequest(
                "POST",
                "/api/admin/organization-requests/" + organizationId + "/approve",
                adminSession,
                approveHeaders,
                null
        );

        assertEquals(200, approveResult.status, "Step 3 must return 200 OK.");
        assertTrue(approveResult.body.contains("\"status\":\"APPROVED\""), "Organization status must be APPROVED.");

        // Step 4. Notification Clearing
        ServletExecutionResult clearedListResult = executeRequest(
                "GET",
                "/api/admin/organization-requests/pending",
                adminSession,
                Collections.emptyMap(),
                null
        );

        assertEquals(200, clearedListResult.status, "Step 4 must return 200 OK.");
        long clearedCount = extractCount(clearedListResult.body);
        assertEquals(0, clearedCount, "Pending count must be 0 after approval.");
        assertFalse(clearedListResult.body.contains(organizationId.toString()), "Approved organization must not remain in pending list.");

        boolean notificationBellRedDotExtinguished = (clearedCount == 0);
        assertTrue(notificationBellRedDotExtinguished, "Notification bell red dot badge must be extinguished.");

        // Step 5. Event Schedule and Role Elevation Verification
        OrganizationMembership membership = membershipDatabase.stream()
                .filter(m -> m.getUser().getId().equals(musicianUser.getId()) && m.getOrganization().getId().equals(organizationId))
                .findFirst()
                .orElse(null);

        assertNotNull(membership, "Requester membership must exist in database.");
        assertEquals(OrganizationRole.MANAGER, membership.getRole(), "Requester role must be MANAGER.");

        UserPrincipal elevatedPrincipal = authService.login(new LoginRequestDto("musician@ticketscenter.vn", DEFAULT_PASSWORD));
        assertNotNull(elevatedPrincipal);
        assertEquals(musicianUser.getId(), elevatedPrincipal.actorId());
        assertEquals("musician@ticketscenter.vn", elevatedPrincipal.email());
        assertTrue(elevatedPrincipal.isManager(), "Elevated principal must have isManager equal to true.");

        List<EventDto> managedEvents = eventService.managedEvents(elevatedPrincipal, organizationId);
        assertFalse(managedEvents.isEmpty(), "Organization must have scheduled events.");

        EventDto eventDto = managedEvents.getFirst();
        assertEquals("Live Concert Âm Hưởng Mùa Thu", eventDto.title());
        assertEquals("Nhà Hát Thành Phố", eventDto.venueName());

        ZoneId vietnamZone = ZoneId.of("Asia/Ho_Chi_Minh");
        Instant expectedStartTime = LocalDate.parse("2026-11-20")
                .atTime(LocalTime.parse("19:30"))
                .atZone(vietnamZone)
                .toInstant();
        Instant expectedEndTime = LocalDate.parse("2026-11-20")
                .atTime(LocalTime.parse("22:30"))
                .atZone(vietnamZone)
                .toInstant();

        assertEquals(expectedStartTime, eventDto.startTime(), "Event start time must match scheduled time.");
        assertEquals(expectedEndTime, eventDto.endTime(), "Event end time must match scheduled time.");

        Event persistedEvent = eventDatabase.stream()
                .filter(e -> e.getOrganization().getId().equals(organizationId))
                .findFirst()
                .orElse(null);

        assertNotNull(persistedEvent, "Event entity must be persisted in database.");
        assertEquals("Live Concert Âm Hưởng Mùa Thu", persistedEvent.getTitle());
        assertEquals("Nhà Hát Thành Phố", persistedEvent.getVenueName());
        assertEquals(expectedStartTime, persistedEvent.getStartTime());
        assertEquals(expectedEndTime, persistedEvent.getEndTime());
    }

    private ServletExecutionResult executeRequest(
            String method,
            String uri,
            Map<String, Object> sessionAttributes,
            Map<String, String> requestHeaders,
            String jsonBody
    ) throws Exception {
        AtomicInteger statusHolder = new AtomicInteger(200);
        StringWriter outputWriter = new StringWriter();
        AtomicBoolean filterChainInvoked = new AtomicBoolean(false);

        HttpSession sessionProxy = (HttpSession) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{HttpSession.class},
                (proxy, m, args) -> switch (m.getName()) {
                    case "getAttribute" -> sessionAttributes.get((String) args[0]);
                    case "setAttribute" -> {
                        sessionAttributes.put((String) args[0], args[1]);
                        yield null;
                    }
                    case "removeAttribute" -> {
                        sessionAttributes.remove((String) args[0]);
                        yield null;
                    }
                    case "getId" -> "test-session-id";
                    default -> null;
                }
        );

        HttpServletRequest requestProxy = (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, m, args) -> switch (m.getName()) {
                    case "getMethod" -> method;
                    case "getRequestURI" -> uri;
                    case "getContextPath" -> "";
                    case "getHeader" -> requestHeaders.get((String) args[0]);
                    case "getAttribute" -> null;
                    case "setAttribute" -> null;
                    case "getSession" -> sessionProxy;
                    case "getContentType" -> jsonBody != null ? "application/json" : "";
                    case "getReader" -> new BufferedReader(new StringReader(jsonBody != null ? jsonBody : ""));
                    case "getServletContext" -> null;
                    default -> null;
                }
        );

        HttpServletResponse responseProxy = (HttpServletResponse) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, m, args) -> switch (m.getName()) {
                    case "setStatus" -> {
                        statusHolder.set((Integer) args[0]);
                        yield null;
                    }
                    case "getStatus" -> statusHolder.get();
                    case "getWriter" -> new PrintWriter(outputWriter);
                    case "setHeader", "setContentType", "setCharacterEncoding" -> null;
                    default -> null;
                }
        );

        csrfFilter.doFilter(requestProxy, responseProxy, (req, resp) -> {
            filterChainInvoked.set(true);
            try {
                if ("POST".equalsIgnoreCase(method)) {
                    servlet.doPost((HttpServletRequest) req, (HttpServletResponse) resp);
                } else if ("GET".equalsIgnoreCase(method)) {
                    servlet.doGet((HttpServletRequest) req, (HttpServletResponse) resp);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        assertTrue(filterChainInvoked.get(), "Request must pass CSRF validation and execute servlet.");
        return new ServletExecutionResult(statusHolder.get(), outputWriter.toString());
    }

    private static class ServletExecutionResult {
        final int status;
        final String body;

        ServletExecutionResult(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    private TransactionManager createTransactionManager() {
        EntityTransaction tx = (EntityTransaction) Proxy.newProxyInstance(
                EntityTransaction.class.getClassLoader(),
                new Class<?>[]{EntityTransaction.class},
                (proxy, method, args) -> {
                    if ("isActive".equals(method.getName())) return true;
                    return null;
                }
        );

        EntityManager em = (EntityManager) Proxy.newProxyInstance(
                EntityManager.class.getClassLoader(),
                new Class<?>[]{EntityManager.class},
                (proxy, method, args) -> {
                    if ("getTransaction".equals(method.getName())) return tx;
                    if ("persist".equals(method.getName()) && args != null && args.length > 0) {
                        Object entity = args[0];
                        if (entity instanceof OrganizationMembership m) {
                            membershipDatabase.add(m);
                        } else if (entity instanceof Event ev) {
                            if (ev.getId() == null) {
                                setEntityId(ev, UUID.randomUUID());
                            }
                            eventDatabase.add(ev);
                        }
                        return null;
                    }
                    if ("createQuery".equals(method.getName()) && args != null && args.length > 0) {
                        String query = (String) args[0];
                        if (query.contains("OrganizationMembership")) {
                            return createMembershipQueryProxy();
                        }
                    }
                    return null;
                }
        );

        EntityManagerFactory emf = (EntityManagerFactory) Proxy.newProxyInstance(
                EntityManagerFactory.class.getClassLoader(),
                new Class<?>[]{EntityManagerFactory.class},
                (proxy, method, args) -> {
                    if ("createEntityManager".equals(method.getName())) return em;
                    return null;
                }
        );

        return new TransactionManager(Map.of(DatabasePrincipal.AUTH, emf));
    }

    @SuppressWarnings("unchecked")
    private TypedQuery<OrganizationRole> createMembershipQueryProxy() {
        Map<String, Object> queryParams = new LinkedHashMap<>();
        return (TypedQuery<OrganizationRole>) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{TypedQuery.class},
                (proxy, method, args) -> {
                    if ("setParameter".equals(method.getName())) {
                        queryParams.put((String) args[0], args[1]);
                        return proxy;
                    }
                    if ("setMaxResults".equals(method.getName())) {
                        return proxy;
                    }
                    if ("getResultList".equals(method.getName())) {
                        UUID userId = (UUID) queryParams.get("uid");
                        List<OrganizationRole> roles = membershipDatabase.stream()
                                .filter(m -> m.getUser().getId().equals(userId))
                                .map(OrganizationMembership::getRole)
                                .toList();
                        return roles;
                    }
                    return null;
                }
        );
    }

    private static UUID extractOrganizationId(String json) {
        Pattern pattern = Pattern.compile("\"organizationId\"\\s*:\\s*\"([0-9a-fA-F-]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return UUID.fromString(matcher.group(1));
        }
        return null;
    }

    private static long extractCount(String json) {
        Pattern pattern = Pattern.compile("\"count\"\\s*:\\s*([0-9]+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return Long.parseLong(matcher.group(1));
        }
        return 0;
    }

    private static void setEntityId(Object entity, UUID id) {
        try {
            Field idField = BaseEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
