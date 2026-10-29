package vn.ticketscenter.order.coupon.service;

import redis.clients.jedis.resps.ScanResult;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.ScanParams;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.order.coupon.dto.CouponAdminDto;
import vn.ticketscenter.order.coupon.dto.CreateCouponRequest;
import vn.ticketscenter.order.coupon.dto.UpdateCouponRequest;
import vn.ticketscenter.order.coupon.dao.CouponAdminRepository;
import vn.ticketscenter.order.service.OrderException;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Supplier;

public class CouponAdminServiceImpl implements CouponAdminService {
    private final CouponAdminRepository repository;
    private final Supplier<Jedis> redisSupplier;

    public CouponAdminServiceImpl(CouponAdminRepository repository, Supplier<Jedis> redisSupplier) {
        this.repository = Objects.requireNonNull(repository, "repository is required");
        this.redisSupplier = redisSupplier;
    }

    @Override
    public List<CouponAdminDto> listAllCoupons(
            UserPrincipal principal, int page, int size, UUID filterOrgId) {
        if (principal == null) {
            throw new OrderException(401, "UNAUTHENTICATED", "A current session is required.");
        }
        if (!principal.isAdmin()) {
            throw new OrderException(403, "FORBIDDEN", "Admin access is required.");
        }
        return repository.getAllCoupons(principal, page, size, filterOrgId);
    }

    @Override
    public List<CouponAdminDto> listOrganizationCoupons(
            UserPrincipal principal, UUID organizationId, int page, int size) {
        if (principal == null) {
            throw new OrderException(401, "UNAUTHENTICATED", "A current session is required.");
        }
        if (organizationId == null) {
            throw new OrderException(400, "BAD_REQUEST", "organizationId is required.");
        }

        checkPermission(principal, organizationId);
        return repository.getCouponsByOrganization(principal, organizationId, page, size);
    }

    @Override
    public CouponAdminDto getCoupon(UserPrincipal principal, UUID couponId) {
        if (principal == null) {
            throw new OrderException(401, "UNAUTHENTICATED", "A current session is required.");
        }
        if (couponId == null) {
            throw new OrderException(400, "BAD_REQUEST", "couponId is required.");
        }

        CouponAdminDto coupon =
                repository
                        .findById(principal, couponId)
                        .orElseThrow(
                                () ->
                                        new OrderException(
                                                404, "COUPON_NOT_FOUND", "Coupon was not found."));

        checkPermission(principal, coupon.organizationId());
        return coupon;
    }

    @Override
    public CouponAdminDto createCoupon(
            UserPrincipal principal, UUID organizationId, CreateCouponRequest req) {
        if (principal == null) {
            throw new OrderException(401, "UNAUTHENTICATED", "A current session is required.");
        }
        if (organizationId == null) {
            throw new OrderException(400, "BAD_REQUEST", "organizationId is required.");
        }
        Objects.requireNonNull(req, "request is required");

        checkPermission(principal, organizationId);

        // business rules
        if (!req.validFrom().isBefore(req.validTo())) {
            throw new OrderException(400, "INVALID_VALIDITY", "validFrom must be before validTo.");
        }
        if (req.maxUses() <= 0) {
            throw new OrderException(400, "INVALID_MAX_USES", "maxUses must be greater than zero.");
        }

        if ("PERCENTAGE".equalsIgnoreCase(req.discountType())) {
            if (req.percentageValue() == null
                    || req.percentageValue().compareTo(BigDecimal.ZERO) <= 0
                    || req.percentageValue().compareTo(new BigDecimal("30")) > 0) {
                throw new OrderException(
                        400,
                        "INVALID_DISCOUNT_PERCENTAGE",
                        "Percentage must be positive and at most 30%.");
            }
        } else if ("FIXED_AMOUNT".equalsIgnoreCase(req.discountType())) {
            if (req.fixedAmount() == null || req.fixedAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new OrderException(
                        400, "INVALID_FIXED_AMOUNT", "Fixed amount must be positive.");
            }
        } else {
            throw new OrderException(
                    400,
                    "INVALID_DISCOUNT_TYPE",
                    "Discount type must be PERCENTAGE or FIXED_AMOUNT.");
        }

        UUID createdId = repository.createCoupon(principal, organizationId, req);
        invalidateCouponCache(organizationId);
        return repository.findById(principal, createdId).orElseThrow();
    }

    @Override
    public CouponAdminDto updateCoupon(
            UserPrincipal principal, UUID couponId, UpdateCouponRequest req) {
        if (principal == null) {
            throw new OrderException(401, "UNAUTHENTICATED", "A current session is required.");
        }
        if (couponId == null) {
            throw new OrderException(400, "BAD_REQUEST", "couponId is required.");
        }
        Objects.requireNonNull(req, "request is required");

        CouponAdminDto existing =
                repository
                        .findById(principal, couponId)
                        .orElseThrow(
                                () ->
                                        new OrderException(
                                                404, "COUPON_NOT_FOUND", "Coupon was not found."));

        checkPermission(principal, existing.organizationId());

        // maxUses may not drop below used + held
        if (req.maxUses() != null) {
            long currentUsage = existing.reservedUses() + existing.consumedUses();
            if (req.maxUses() < currentUsage) {
                throw new OrderException(
                        409,
                        "COUPON_USAGE_CONFLICT",
                        "Cannot reduce maxUses below current reserved + consumed usage: "
                                + currentUsage);
            }
        }
        if (req.validTo() != null && !existing.validFrom().isBefore(req.validTo())) {
            throw new OrderException(400, "INVALID_VALIDITY", "validTo must be after validFrom.");
        }

        repository.updateCoupon(principal, couponId, req);
        invalidateCouponCache(existing.organizationId());
        return repository.findById(principal, couponId).orElseThrow();
    }

    @Override
    public void setStatus(UserPrincipal principal, UUID couponId, boolean active) {
        if (principal == null) {
            throw new OrderException(401, "UNAUTHENTICATED", "A current session is required.");
        }
        CouponAdminDto existing =
                repository
                        .findById(principal, couponId)
                        .orElseThrow(
                                () ->
                                        new OrderException(
                                                404, "COUPON_NOT_FOUND", "Coupon was not found."));

        checkPermission(principal, existing.organizationId());
        repository.setActive(principal, couponId, active);
        invalidateCouponCache(existing.organizationId());
    }

    @Override
    public void deleteCoupon(UserPrincipal principal, UUID couponId) {
        if (principal == null) {
            throw new OrderException(401, "UNAUTHENTICATED", "A current session is required.");
        }
        CouponAdminDto existing =
                repository
                        .findById(principal, couponId)
                        .orElseThrow(
                                () ->
                                        new OrderException(
                                                404, "COUPON_NOT_FOUND", "Coupon was not found."));

        checkPermission(principal, existing.organizationId());

        if (existing.reservedUses() > 0 || existing.consumedUses() > 0) {
            throw new OrderException(
                    409,
                    "CANNOT_DELETE_ACTIVE_REDEMPTIONS",
                    "Cannot delete coupon that has redemptions.");
        }

        repository.deleteCoupon(principal, couponId);
        invalidateCouponCache(existing.organizationId());
    }

    private void checkPermission(UserPrincipal principal, UUID organizationId) {
        if (principal.isAdmin()) {
            return;
        }
        boolean allowed = repository.isManagerOrAdmin(principal, organizationId);
        if (!allowed) {
            throw new OrderException(
                    403,
                    "FORBIDDEN",
                    "Manager permission for organization " + organizationId + " is required.");
        }
    }

    private void invalidateCouponCache(UUID organizationId) {
        if (redisSupplier == null) {
            return;
        }
        try (Jedis redis = redisSupplier.get()) {
            if (redis == null) {
                return;
            }
            // drop the organisation's cached coupon list
            redis.del("tc:coupon:list:v1:" + organizationId);

            // scan and drop the related eligibility entries
            String cursor = "0";
            ScanParams params = new ScanParams().match("tc:order:coupon:*").count(100);
            do {
                ScanResult<String> scanResult = redis.scan(cursor, params);
                List<String> keys = scanResult.getResult();
                if (keys != null && !keys.isEmpty()) {
                    redis.del(keys.toArray(String[]::new));
                }
                cursor = scanResult.getCursor();
            } while (!"0".equals(cursor));
        } catch (Exception ignored) {
            // a cache failure does not undo the database change
        }
    }
}
