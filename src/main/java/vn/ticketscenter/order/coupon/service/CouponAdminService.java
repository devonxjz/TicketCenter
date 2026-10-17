package vn.ticketscenter.order.coupon.service;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.ScanParams;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.order.coupon.dto.CouponAdminDto;
import vn.ticketscenter.order.coupon.dto.CreateCouponRequest;
import vn.ticketscenter.order.coupon.dto.UpdateCouponRequest;
import vn.ticketscenter.order.coupon.repository.CouponAdminRepository;
import vn.ticketscenter.order.service.OrderException;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Supplier;

/**
 * Nghiệp vụ quản trị Coupon cho Ban tổ chức và Admin (LIEM-06 / UI-14).
 */
public class CouponAdminService {
    private final CouponAdminRepository repository;
    private final Supplier<Jedis> redisSupplier;

    public CouponAdminService(CouponAdminRepository repository, Supplier<Jedis> redisSupplier) {
        this.repository = Objects.requireNonNull(repository, "repository is required");
        this.redisSupplier = redisSupplier;
    }

    public List<CouponAdminDto> listAllCoupons(
            UserPrincipal principal, int page, int size, UUID filterOrgId) {
        if (principal == null)
            throw new OrderException(401, "UNAUTHENTICATED", "A current session is required.");
        if (!principal.isAdmin())
            throw new OrderException(403, "FORBIDDEN", "Admin access is required.");
        return repository.getAllCoupons(principal, page, size, filterOrgId);
    }

    public List<CouponAdminDto> listOrganizationCoupons(
            UserPrincipal principal, UUID organizationId, int page, int size) {
        if (principal == null)
            throw new OrderException(401, "UNAUTHENTICATED", "A current session is required.");
        if (organizationId == null)
            throw new OrderException(400, "BAD_REQUEST", "organizationId is required.");

        checkPermission(principal, organizationId);
        return repository.getCouponsByOrganization(principal, organizationId, page, size);
    }

    public CouponAdminDto getCoupon(UserPrincipal principal, UUID couponId) {
        if (principal == null)
            throw new OrderException(401, "UNAUTHENTICATED", "A current session is required.");
        if (couponId == null) throw new OrderException(400, "BAD_REQUEST", "couponId is required.");

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

    public CouponAdminDto createCoupon(
            UserPrincipal principal, UUID organizationId, CreateCouponRequest req) {
        if (principal == null)
            throw new OrderException(401, "UNAUTHENTICATED", "A current session is required.");
        if (organizationId == null)
            throw new OrderException(400, "BAD_REQUEST", "organizationId is required.");
        Objects.requireNonNull(req, "request is required");

        checkPermission(principal, organizationId);

        // Kiểm tra ràng buộc nghiệp vụ
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

    public CouponAdminDto updateCoupon(
            UserPrincipal principal, UUID couponId, UpdateCouponRequest req) {
        if (principal == null)
            throw new OrderException(401, "UNAUTHENTICATED", "A current session is required.");
        if (couponId == null) throw new OrderException(400, "BAD_REQUEST", "couponId is required.");
        Objects.requireNonNull(req, "request is required");

        CouponAdminDto existing =
                repository
                        .findById(principal, couponId)
                        .orElseThrow(
                                () ->
                                        new OrderException(
                                                404, "COUPON_NOT_FOUND", "Coupon was not found."));

        checkPermission(principal, existing.organizationId());

        // Kiểm tra không được hạ maxUses dưới số lượng đã dùng + đang giữ
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

    public void setStatus(UserPrincipal principal, UUID couponId, boolean active) {
        if (principal == null)
            throw new OrderException(401, "UNAUTHENTICATED", "A current session is required.");
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

    public void deleteCoupon(UserPrincipal principal, UUID couponId) {
        if (principal == null)
            throw new OrderException(401, "UNAUTHENTICATED", "A current session is required.");
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
        if (principal.isAdmin()) return;
        boolean allowed = repository.isManagerOrAdmin(principal, organizationId);
        if (!allowed) {
            throw new OrderException(
                    403,
                    "FORBIDDEN",
                    "Manager permission for organization " + organizationId + " is required.");
        }
    }

    private void invalidateCouponCache(UUID organizationId) {
        if (redisSupplier == null) return;
        try (Jedis redis = redisSupplier.get()) {
            if (redis == null) return;
            // Xóa cache danh sách coupon của tổ chức
            redis.del("tc:coupon:list:v1:" + organizationId);

            // Quét và xóa các eligibility cache liên quan
            String cursor = "0";
            ScanParams params = new ScanParams().match("tc:order:coupon:*").count(100);
            do {
                var scanResult = redis.scan(cursor, params);
                var keys = scanResult.getResult();
                if (keys != null && !keys.isEmpty()) {
                    redis.del(keys.toArray(String[]::new));
                }
                cursor = scanResult.getCursor();
            } while (!"0".equals(cursor));
        } catch (Exception ignored) {
            // Lỗi cache không làm gián đoạn nghiệp vụ DB
        }
    }
}
