package vn.ticketscenter.order.coupon.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.order.coupon.dto.CouponAdminDto;
import vn.ticketscenter.order.coupon.dto.CreateCouponRequest;
import vn.ticketscenter.order.coupon.dto.UpdateCouponRequest;
import vn.ticketscenter.order.coupon.repository.CouponAdminRepository;
import vn.ticketscenter.order.service.OrderException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CouponAdminServiceTest {

    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MANAGER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID ORG_1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORG_2 = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private UserPrincipal adminPrincipal;
    private UserPrincipal managerPrincipal;
    private UserPrincipal customerPrincipal;

    private FakeCouponAdminRepository fakeRepo;
    private CouponAdminService service;

    @BeforeEach
    void setUp() {
        adminPrincipal = new UserPrincipal(ADMIN_ID, PlatformRole.ADMIN, 1);
        managerPrincipal = new UserPrincipal(MANAGER_ID, PlatformRole.CUSTOMER, 1);
        customerPrincipal = new UserPrincipal(CUSTOMER_ID, PlatformRole.CUSTOMER, 1);

        fakeRepo = new FakeCouponAdminRepository();
        service = new CouponAdminService(fakeRepo, null);
    }

    @Test
    void unauthenticatedUserThrows401() {
        OrderException ex1 = assertThrows(OrderException.class,
                () -> service.listAllCoupons(null, 1, 10, null));
        assertEquals(401, ex1.status());

        OrderException ex2 = assertThrows(OrderException.class,
                () -> service.listOrganizationCoupons(null, ORG_1, 1, 10));
        assertEquals(401, ex2.status());

        OrderException ex3 = assertThrows(OrderException.class,
                () -> service.getCoupon(null, UUID.randomUUID()));
        assertEquals(401, ex3.status());
    }

    @Test
    void listAllCouponsRequiresAdmin() {
        OrderException ex = assertThrows(OrderException.class,
                () -> service.listAllCoupons(managerPrincipal, 1, 10, null));
        assertEquals(403, ex.status());
        assertEquals("FORBIDDEN", ex.code());

        List<CouponAdminDto> result = service.listAllCoupons(adminPrincipal, 1, 10, null);
        assertNotNull(result);
    }

    @Test
    void listOrganizationCouponsPermissionCheck() {
        fakeRepo.managers.put(MANAGER_ID, Set.of(ORG_1));

        // Customer has no manager role
        OrderException exCustomer = assertThrows(OrderException.class,
                () -> service.listOrganizationCoupons(customerPrincipal, ORG_1, 1, 10));
        assertEquals(403, exCustomer.status());

        // Manager of ORG_1 can access ORG_1
        List<CouponAdminDto> org1List = service.listOrganizationCoupons(managerPrincipal, ORG_1, 1, 10);
        assertNotNull(org1List);

        // Manager of ORG_1 cannot access ORG_2
        OrderException exOrg2 = assertThrows(OrderException.class,
                () -> service.listOrganizationCoupons(managerPrincipal, ORG_2, 1, 10));
        assertEquals(403, exOrg2.status());

        // Admin can access any organization
        List<CouponAdminDto> adminOrg2List = service.listOrganizationCoupons(adminPrincipal, ORG_2, 1, 10);
        assertNotNull(adminOrg2List);
    }

    @Test
    void getCouponChecksExistenceAndPermission() {
        UUID couponId = UUID.randomUUID();
        fakeRepo.managers.put(MANAGER_ID, Set.of(ORG_1));

        // 404 if not found
        OrderException exNotFound = assertThrows(OrderException.class,
                () -> service.getCoupon(adminPrincipal, couponId));
        assertEquals(404, exNotFound.status());

        // Coupon belongs to ORG_2
        CouponAdminDto couponInOrg2 = sampleCoupon(couponId, ORG_2, "TEST20", 0, 0);
        fakeRepo.coupons.put(couponId, couponInOrg2);

        // Manager of ORG_1 cannot get coupon of ORG_2
        OrderException exForbidden = assertThrows(OrderException.class,
                () -> service.getCoupon(managerPrincipal, couponId));
        assertEquals(403, exForbidden.status());

        // Admin can get it
        CouponAdminDto retrieved = service.getCoupon(adminPrincipal, couponId);
        assertEquals(couponId, retrieved.id());
    }

    @Test
    void createCouponValidatesBusinessRules() {
        fakeRepo.managers.put(MANAGER_ID, Set.of(ORG_1));
        Instant now = Instant.now();

        // validFrom >= validTo
        CreateCouponRequest invalidDateReq = new CreateCouponRequest(
                "CODE", "PERCENTAGE", new BigDecimal("10"), null, null, 100,
                now.plus(2, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS)
        );
        OrderException exDate = assertThrows(OrderException.class,
                () -> service.createCoupon(managerPrincipal, ORG_1, invalidDateReq));
        assertEquals(400, exDate.status());
        assertEquals("INVALID_VALIDITY", exDate.code());

        // maxUses <= 0
        CreateCouponRequest invalidUsesReq = new CreateCouponRequest(
                "CODE", "PERCENTAGE", new BigDecimal("10"), null, null, 0,
                now, now.plus(1, ChronoUnit.DAYS)
        );
        OrderException exUses = assertThrows(OrderException.class,
                () -> service.createCoupon(managerPrincipal, ORG_1, invalidUsesReq));
        assertEquals(400, exUses.status());
        assertEquals("INVALID_MAX_USES", exUses.code());

        // percentage > 30%
        CreateCouponRequest excessPctReq = new CreateCouponRequest(
                "CODE", "PERCENTAGE", new BigDecimal("35"), null, null, 100,
                now, now.plus(1, ChronoUnit.DAYS)
        );
        OrderException exPct = assertThrows(OrderException.class,
                () -> service.createCoupon(managerPrincipal, ORG_1, excessPctReq));
        assertEquals(400, exPct.status());
        assertEquals("INVALID_DISCOUNT_PERCENTAGE", exPct.code());

        // percentage <= 0
        CreateCouponRequest zeroPctReq = new CreateCouponRequest(
                "CODE", "PERCENTAGE", BigDecimal.ZERO, null, null, 100,
                now, now.plus(1, ChronoUnit.DAYS)
        );
        OrderException exZeroPct = assertThrows(OrderException.class,
                () -> service.createCoupon(managerPrincipal, ORG_1, zeroPctReq));
        assertEquals(400, exZeroPct.status());
        assertEquals("INVALID_DISCOUNT_PERCENTAGE", exZeroPct.code());

        // fixed amount <= 0
        CreateCouponRequest zeroFixedReq = new CreateCouponRequest(
                "CODE", "FIXED_AMOUNT", null, BigDecimal.ZERO, null, 100,
                now, now.plus(1, ChronoUnit.DAYS)
        );
        OrderException exFixed = assertThrows(OrderException.class,
                () -> service.createCoupon(managerPrincipal, ORG_1, zeroFixedReq));
        assertEquals(400, exFixed.status());
        assertEquals("INVALID_FIXED_AMOUNT", exFixed.code());

        // invalid discount type
        CreateCouponRequest invalidTypeReq = new CreateCouponRequest(
                "CODE", "UNKNOWN", null, null, null, 100,
                now, now.plus(1, ChronoUnit.DAYS)
        );
        OrderException exType = assertThrows(OrderException.class,
                () -> service.createCoupon(managerPrincipal, ORG_1, invalidTypeReq));
        assertEquals(400, exType.status());
        assertEquals("INVALID_DISCOUNT_TYPE", exType.code());
    }

    @Test
    void createCouponSuccess() {
        fakeRepo.managers.put(MANAGER_ID, Set.of(ORG_1));
        Instant now = Instant.now();

        CreateCouponRequest validReq = new CreateCouponRequest(
                "SAVE20", "PERCENTAGE", new BigDecimal("20"), null, new BigDecimal("50000"), 100,
                now, now.plus(7, ChronoUnit.DAYS)
        );

        CouponAdminDto created = service.createCoupon(managerPrincipal, ORG_1, validReq);
        assertNotNull(created);
        assertEquals("SAVE20", created.code());
        assertEquals(100, created.maxUses());
        assertEquals(ORG_1, created.organizationId());
    }

    @Test
    void updateCouponRejectsLoweringMaxUsesBelowUsage() {
        UUID couponId = UUID.randomUUID();
        fakeRepo.managers.put(MANAGER_ID, Set.of(ORG_1));

        // Coupon with reserved=5, consumed=15 (total usage = 20)
        CouponAdminDto existing = sampleCoupon(couponId, ORG_1, "TEST", 5, 15);
        fakeRepo.coupons.put(couponId, existing);

        // Trying to reduce maxUses to 18 (< 20)
        UpdateCouponRequest req = new UpdateCouponRequest(18, null, null);
        OrderException ex = assertThrows(OrderException.class,
                () -> service.updateCoupon(managerPrincipal, couponId, req));
        assertEquals(409, ex.status());
        assertEquals("COUPON_USAGE_CONFLICT", ex.code());

        // Updating maxUses to 25 (>= 20) succeeds
        UpdateCouponRequest validReq = new UpdateCouponRequest(25, null, null);
        CouponAdminDto updated = service.updateCoupon(managerPrincipal, couponId, validReq);
        assertEquals(25, updated.maxUses());
    }

    @Test
    void updateCouponValidatesValidTo() {
        UUID couponId = UUID.randomUUID();
        fakeRepo.managers.put(MANAGER_ID, Set.of(ORG_1));
        CouponAdminDto existing = sampleCoupon(couponId, ORG_1, "TEST", 0, 0);
        fakeRepo.coupons.put(couponId, existing);

        // validTo before existing validFrom
        UpdateCouponRequest req = new UpdateCouponRequest(null, existing.validFrom().minus(1, ChronoUnit.HOURS), null);
        OrderException ex = assertThrows(OrderException.class,
                () -> service.updateCoupon(managerPrincipal, couponId, req));
        assertEquals(400, ex.status());
        assertEquals("INVALID_VALIDITY", ex.code());
    }

    @Test
    void setStatusTogglesActive() {
        UUID couponId = UUID.randomUUID();
        fakeRepo.managers.put(MANAGER_ID, Set.of(ORG_1));
        CouponAdminDto existing = sampleCoupon(couponId, ORG_1, "TEST", 0, 0);
        fakeRepo.coupons.put(couponId, existing);

        service.setStatus(managerPrincipal, couponId, false);
        assertFalse(fakeRepo.coupons.get(couponId).active());

        service.setStatus(managerPrincipal, couponId, true);
        assertTrue(fakeRepo.coupons.get(couponId).active());
    }

    @Test
    void deleteCouponRejectsWhenRedemptionsExist() {
        UUID couponId = UUID.randomUUID();
        fakeRepo.managers.put(MANAGER_ID, Set.of(ORG_1));

        // Consumed uses = 1
        CouponAdminDto usedCoupon = sampleCoupon(couponId, ORG_1, "USED", 0, 1);
        fakeRepo.coupons.put(couponId, usedCoupon);

        OrderException ex = assertThrows(OrderException.class,
                () -> service.deleteCoupon(managerPrincipal, couponId));
        assertEquals(409, ex.status());
        assertEquals("CANNOT_DELETE_ACTIVE_REDEMPTIONS", ex.code());

        // Unused coupon can be deleted
        UUID unusedId = UUID.randomUUID();
        CouponAdminDto unusedCoupon = sampleCoupon(unusedId, ORG_1, "UNUSED", 0, 0);
        fakeRepo.coupons.put(unusedId, unusedCoupon);

        assertDoesNotThrow(() -> service.deleteCoupon(managerPrincipal, unusedId));
        assertFalse(fakeRepo.coupons.containsKey(unusedId));
    }

    private static CouponAdminDto sampleCoupon(UUID id, UUID orgId, String code, int reserved, int consumed) {
        Instant now = Instant.now();
        int maxUses = 100;
        int available = maxUses - (reserved + consumed);
        return new CouponAdminDto(
                id, orgId, "Test Org", code, "PERCENTAGE", new BigDecimal("20"),
                null, new BigDecimal("50000"), maxUses, reserved, consumed, available,
                now.minus(1, ChronoUnit.DAYS), now.plus(7, ChronoUnit.DAYS), true, 1L
        );
    }

    private static class FakeCouponAdminRepository extends CouponAdminRepository {
        final Map<UUID, Set<UUID>> managers = new HashMap<>();
        final Map<UUID, CouponAdminDto> coupons = new HashMap<>();

        @Override
        public boolean isManagerOrAdmin(UUID actorId, boolean isAdmin, UUID organizationId) {
            if (isAdmin) return true;
            return managers.getOrDefault(actorId, Set.of()).contains(organizationId);
        }

        @Override
        public List<CouponAdminDto> getAllCoupons(int page, int size, UUID filterOrgId) {
            return coupons.values().stream()
                    .filter(c -> filterOrgId == null || c.organizationId().equals(filterOrgId))
                    .toList();
        }

        @Override
        public List<CouponAdminDto> getCouponsByOrganization(UUID organizationId, int page, int size) {
            return getAllCoupons(page, size, organizationId);
        }

        @Override
        public Optional<CouponAdminDto> findById(UUID couponId) {
            return Optional.ofNullable(coupons.get(couponId));
        }

        @Override
        public UUID createCoupon(UUID organizationId, CreateCouponRequest req) {
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
        public void updateCoupon(UUID couponId, UpdateCouponRequest req) {
            CouponAdminDto cur = coupons.get(couponId);
            if (cur != null) {
                int maxUses = req.maxUses() != null ? req.maxUses() : cur.maxUses();
                Instant validTo = req.validTo() != null ? req.validTo() : cur.validTo();
                int avail = maxUses - (cur.reservedUses() + cur.consumedUses());
                coupons.put(couponId, new CouponAdminDto(
                        cur.id(), cur.organizationId(), cur.organizationName(), cur.code(),
                        cur.discountType(), cur.percentageValue(), cur.fixedAmount(), cur.maxDiscountAmount(),
                        maxUses, cur.reservedUses(), cur.consumedUses(), avail,
                        cur.validFrom(), validTo, cur.active(), cur.version() + 1
                ));
            }
        }

        @Override
        public void setActive(UUID couponId, boolean active) {
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
        public void deleteCoupon(UUID couponId) {
            coupons.remove(couponId);
        }
    }
}
