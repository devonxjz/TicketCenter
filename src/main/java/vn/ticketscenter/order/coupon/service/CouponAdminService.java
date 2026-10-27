package vn.ticketscenter.order.coupon.service;

import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.order.coupon.dto.CouponAdminDto;
import vn.ticketscenter.order.coupon.dto.CreateCouponRequest;
import vn.ticketscenter.order.coupon.dto.UpdateCouponRequest;
import java.util.*;

/**
 * Nghiệp vụ quản trị Coupon cho Ban tổ chức và Admin (LIEM-06 / UI-14).
 */
public interface CouponAdminService {
    List<CouponAdminDto> listAllCoupons(
            UserPrincipal principal, int page, int size, UUID filterOrgId);

    List<CouponAdminDto> listOrganizationCoupons(
            UserPrincipal principal, UUID organizationId, int page, int size);

    CouponAdminDto getCoupon(UserPrincipal principal, UUID couponId);

    CouponAdminDto createCoupon(
            UserPrincipal principal, UUID organizationId, CreateCouponRequest req);

    CouponAdminDto updateCoupon(UserPrincipal principal, UUID couponId, UpdateCouponRequest req);

    void setStatus(UserPrincipal principal, UUID couponId, boolean active);

    void deleteCoupon(UserPrincipal principal, UUID couponId);
}
