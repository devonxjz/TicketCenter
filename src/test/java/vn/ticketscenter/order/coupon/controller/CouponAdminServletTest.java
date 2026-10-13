package vn.ticketscenter.order.coupon.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.order.coupon.dto.CouponAdminDto;
import vn.ticketscenter.order.coupon.dto.CreateCouponRequest;
import vn.ticketscenter.order.coupon.dto.UpdateCouponRequest;
import vn.ticketscenter.order.coupon.repository.CouponAdminRepository;
import vn.ticketscenter.order.coupon.service.CouponAdminService;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CouponAdminServletTest {

    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ORG_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID COUPON_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private UserPrincipal adminPrincipal;
    private CouponAdminServlet servlet;
    private FakeRepo fakeRepo;

    @BeforeEach
    void setUp() {
        adminPrincipal = new UserPrincipal(ADMIN_ID, PlatformRole.ADMIN, 1);
        fakeRepo = new FakeRepo();
        CouponAdminService service = new CouponAdminService(fakeRepo, null);
        servlet = new CouponAdminServlet(service);
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        Result r = call(null, "GET", "/api/admin/coupons", null, null, Map.of());
        assertEquals(401, r.status);
        assertTrue(r.body.toString().contains("UNAUTHENTICATED"));
    }

    @Test
    void missingIdempotencyKeyOnMutationReturns400() throws Exception {
        Result r = call(adminPrincipal, "POST", "/api/organizations/" + ORG_ID + "/coupons", null, null, Map.of());
        assertEquals(400, r.status);
        assertTrue(r.body.toString().contains("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void getAdminCouponsReturns200() throws Exception {
        fakeRepo.addSample(COUPON_ID, ORG_ID, "SUMMER20");
        Result r = call(adminPrincipal, "GET", "/api/admin/coupons", null, null, Map.of());
        assertEquals(200, r.status);
        assertTrue(r.body.toString().contains("SUMMER20"));
    }

    @Test
    void getOrganizationCouponsReturns200() throws Exception {
        fakeRepo.addSample(COUPON_ID, ORG_ID, "SUMMER20");
        Result r = call(adminPrincipal, "GET", "/api/organizations/" + ORG_ID + "/coupons", null, null, Map.of());
        assertEquals(200, r.status);
        assertTrue(r.body.toString().contains("SUMMER20"));
    }

    @Test
    void getCouponDetailReturns200() throws Exception {
        fakeRepo.addSample(COUPON_ID, ORG_ID, "DETAIL20");
        Result r = call(adminPrincipal, "GET", "/api/coupons/" + COUPON_ID, null, null, Map.of());
        assertEquals(200, r.status);
        assertTrue(r.body.toString().contains("DETAIL20"));
    }

    @Test
    void postCreateCouponReturns201() throws Exception {
        Instant now = Instant.now();
        String json = "{"
                + "\"code\":\"NEWCODE\","
                + "\"discountType\":\"PERCENTAGE\","
                + "\"percentageValue\":15,"
                + "\"maxUses\":50,"
                + "\"validFrom\":\"" + now + "\","
                + "\"validTo\":\"" + now.plus(5, ChronoUnit.DAYS) + "\""
                + "}";
        Result r = call(adminPrincipal, "POST", "/api/organizations/" + ORG_ID + "/coupons",
                UUID.randomUUID().toString(), json, Map.of());
        assertEquals(201, r.status);
        assertTrue(r.body.toString().contains("NEWCODE"));
    }

    @Test
    void activateAndDeactivateCouponReturn200() throws Exception {
        fakeRepo.addSample(COUPON_ID, ORG_ID, "TOGGLE20");

        Result rDeactivate = call(adminPrincipal, "POST", "/api/coupons/" + COUPON_ID + "/deactivate",
                UUID.randomUUID().toString(), null, Map.of());
        assertEquals(200, rDeactivate.status);
        assertTrue(rDeactivate.body.toString().contains("\"active\":false"));

        Result rActivate = call(adminPrincipal, "POST", "/api/coupons/" + COUPON_ID + "/activate",
                UUID.randomUUID().toString(), null, Map.of());
        assertEquals(200, rActivate.status);
        assertTrue(rActivate.body.toString().contains("\"active\":true"));
    }

    @Test
    void deleteCouponReturns200() throws Exception {
        fakeRepo.addSample(COUPON_ID, ORG_ID, "DEL20");

        Result r = call(adminPrincipal, "DELETE", "/api/coupons/" + COUPON_ID,
                UUID.randomUUID().toString(), null, Map.of());
        assertEquals(200, r.status);
        assertTrue(r.body.toString().contains("\"deleted\":true"));
        assertNull(fakeRepo.coupons.get(COUPON_ID));
    }

    @Test
    void putUpdateCouponReturns200() throws Exception {
        fakeRepo.addSample(COUPON_ID, ORG_ID, "UPDATE20");
        String json = "{\"maxUses\":150}";

        Result r = call(adminPrincipal, "PUT", "/api/coupons/" + COUPON_ID,
                UUID.randomUUID().toString(), json, Map.of());
        assertEquals(200, r.status);
        assertTrue(r.body.toString().contains("150"));
    }

    @Test
    void mutationWithoutCsrfTokenIsRejected() throws Exception {
        csrfHeader = null;
        try {
            Result r = call(adminPrincipal, "POST", "/api/coupons/" + COUPON_ID + "/deactivate",
                    UUID.randomUUID().toString(), null, Map.of());
            assertEquals(403, r.status);
        } finally {
            csrfHeader = CSRF;
        }
    }

    private static final String CSRF = "test-csrf-token";
    private String csrfHeader = CSRF;

    private Result call(UserPrincipal actor, String method, String uri, String idemKey, String body, Map<String, String> params) throws Exception {
        Result result = new Result();
        jakarta.servlet.http.HttpSession session = (jakarta.servlet.http.HttpSession) Proxy.newProxyInstance(
                jakarta.servlet.http.HttpSession.class.getClassLoader(),
                new Class<?>[]{jakarta.servlet.http.HttpSession.class},
                (proxy, m, args) -> "getAttribute".equals(m.getName())
                        && SecurityContext.CSRF_TOKEN_ATTRIBUTE.equals(args[0]) ? CSRF : null);
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, m, args) -> switch (m.getName()) {
                    case "getMethod" -> method;
                    case "getRequestURI" -> uri;
                    case "getContextPath" -> "";
                    case "getAttribute" -> SecurityContext.REQUEST_USER_ATTRIBUTE.equals(args[0]) ? actor : null;
                    case "getHeader" -> "Idempotency-Key".equalsIgnoreCase((String) args[0]) ? idemKey
                            : SecurityContext.CSRF_HEADER_NAME.equalsIgnoreCase((String) args[0]) ? csrfHeader : null;
                    case "getSession" -> session;
                    case "getParameter" -> params.get((String) args[0]);
                    case "getReader" -> new BufferedReader(new StringReader(body != null ? body : ""));
                    case "getContentLengthLong" -> (long) (body != null ? body.length() : 0);
                    default -> null;
                }
        );

        HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, m, args) -> {
                    if ("setStatus".equals(m.getName())) result.status = (Integer) args[0];
                    if ("sendError".equals(m.getName())) result.status = (Integer) args[0];
                    if ("getWriter".equals(m.getName())) return new PrintWriter(result.body);
                    return null;
                }
        );

        servlet.service(request, response);
        return result;
    }

    static class Result {
        int status = 200;
        StringWriter body = new StringWriter();
    }

    private static class FakeRepo extends CouponAdminRepository {
        final Map<UUID, CouponAdminDto> coupons = new HashMap<>();

        void addSample(UUID id, UUID orgId, String code) {
            Instant now = Instant.now();
            coupons.put(id, new CouponAdminDto(
                    id, orgId, "Org", code, "PERCENTAGE", new BigDecimal("20"),
                    null, new BigDecimal("50000"), 100, 0, 0, 100,
                    now.minus(1, ChronoUnit.DAYS), now.plus(7, ChronoUnit.DAYS), true, 1L
            ));
        }

        @Override
        public boolean isManagerOrAdmin(UserPrincipal principal, UUID organizationId) {
            return principal.isAdmin();
        }

        @Override
        public List<CouponAdminDto> getAllCoupons(UserPrincipal principal, int page, int size, UUID filterOrgId) {
            return new ArrayList<>(coupons.values());
        }

        @Override
        public List<CouponAdminDto> getCouponsByOrganization(UserPrincipal principal, UUID organizationId, int page, int size) {
            return coupons.values().stream().filter(c -> c.organizationId().equals(organizationId)).toList();
        }

        @Override
        public Optional<CouponAdminDto> findById(UserPrincipal principal, UUID couponId) {
            return Optional.ofNullable(coupons.get(couponId));
        }

        @Override
        public UUID createCoupon(UserPrincipal principal, UUID organizationId, CreateCouponRequest req) {
            UUID id = UUID.randomUUID();
            CouponAdminDto dto = new CouponAdminDto(
                    id, organizationId, "Org", req.code(), req.discountType(),
                    req.percentageValue(), req.fixedAmount(), req.maxDiscountAmount(),
                    req.maxUses(), 0, 0, req.maxUses(),
                    req.validFrom(), req.validTo(), true, 1L
            );
            coupons.put(id, dto);
            return id;
        }

        @Override
        public void updateCoupon(UserPrincipal principal, UUID couponId, UpdateCouponRequest req) {
            CouponAdminDto cur = coupons.get(couponId);
            if (cur != null) {
                int max = req.maxUses() != null ? req.maxUses() : cur.maxUses();
                coupons.put(couponId, new CouponAdminDto(
                        cur.id(), cur.organizationId(), cur.organizationName(), cur.code(),
                        cur.discountType(), cur.percentageValue(), cur.fixedAmount(), cur.maxDiscountAmount(),
                        max, cur.reservedUses(), cur.consumedUses(), max,
                        cur.validFrom(), cur.validTo(), cur.active(), cur.version() + 1
                ));
            }
        }

        @Override
        public void setActive(UserPrincipal principal, UUID couponId, boolean active) {
            CouponAdminDto cur = coupons.get(couponId);
            if (cur != null) {
                coupons.put(couponId, new CouponAdminDto(
                        cur.id(), cur.organizationId(), cur.organizationName(), cur.code(),
                        cur.discountType(), cur.percentageValue(), cur.fixedAmount(), cur.maxDiscountAmount(),
                        cur.maxUses(), cur.reservedUses(), cur.consumedUses(), cur.availableUses(),
                        cur.validFrom(), cur.validTo(), active, cur.version() + 1
                ));
            }
        }

        @Override
        public void deleteCoupon(UserPrincipal principal, UUID couponId) {
            coupons.remove(couponId);
        }
    }
}
