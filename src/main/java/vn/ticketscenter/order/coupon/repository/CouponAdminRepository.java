package vn.ticketscenter.order.coupon.repository;

import vn.ticketscenter.config.persistence.SqlCall;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.order.coupon.dto.CouponAdminDto;
import vn.ticketscenter.order.coupon.dto.CreateCouponRequest;
import vn.ticketscenter.order.coupon.dto.UpdateCouponRequest;
import vn.ticketscenter.order.service.OrderException;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static vn.ticketscenter.config.persistence.SqlCall.instant;
import static vn.ticketscenter.config.persistence.SqlCall.orNull;

public class CouponAdminRepository {
    private final DataSource manager;
    private final DataSource admin;

    public CouponAdminRepository(DataSource manager, DataSource admin) {
        this.manager = Objects.requireNonNull(manager);
        this.admin = Objects.requireNonNull(admin);
    }

    protected CouponAdminRepository() {
        this.manager = null;
        this.admin = null;
    }

    /** THROW numbers of the coupon procedures, plus unique-key and foreign-key violations. */
    private static final SqlCall.ErrorTable ERRORS =
            SqlCall.ErrorTable.of()
                    .on(51520, m -> error(401, "UNAUTHENTICATED", "A current session is required."))
                    .on(51521, m -> error(403, "FORBIDDEN", "Coupon management permission is required."))
                    .on(51523, m -> error(404, "COUPON_NOT_FOUND", "Coupon was not found."))
                    .on(51524, m -> error(409, "COUPON_IN_USE", "Coupon has existing usage."))
                    .on(51107, m -> error(409, "COUPON_USAGE_CONFLICT", "Coupon max uses is below current usage."))
                    .on(new int[] {2601, 2627}, m -> error(409, "COUPON_CODE_CONFLICT", "Coupon code already exists."))
                    .on(547, m -> error(409, "COUPON_IN_USE", "Coupon is referenced by another record."))
                    .fallback((code, message) -> OrderException.unavailable());

    private static OrderException error(int status, String code, String message) {
        return new OrderException(status, code, message);
    }

    private DataSource source(UserPrincipal principal) {
        return principal.isAdmin() ? admin : manager;
    }

    public boolean isManagerOrAdmin(UserPrincipal principal, UUID organizationId) {
        Boolean allowed =
                call(
                        principal,
                        "EXEC dbo.usp_CanManageCouponOrganization ?, ?, ?",
                        rows -> rows.getBoolean("allowed"),
                        principal.actorId(),
                        principal.authVersion(),
                        organizationId);
        return Boolean.TRUE.equals(allowed);
    }

    public List<CouponAdminDto> getCouponsByOrganization(
            UserPrincipal principal, UUID organizationId, int page, int size) {
        return getAllCoupons(principal, page, size, organizationId);
    }

    public List<CouponAdminDto> getAllCoupons(
            UserPrincipal principal, int page, int size, UUID filterOrgId) {
        return SqlCall.onConnection(
                source(principal),
                connection ->
                        SqlCall.queryList(
                                connection,
                                "EXEC dbo.usp_ListManagedCoupons ?, ?, ?, ?, ?",
                                CouponAdminRepository::mapCoupon,
                                ERRORS,
                                principal.actorId(),
                                principal.authVersion(),
                                filterOrgId,
                                Math.max(1, page),
                                Math.min(100, Math.max(1, size))),
                ERRORS);
    }

    public Optional<CouponAdminDto> findById(UserPrincipal principal, UUID couponId) {
        return Optional.ofNullable(
                call(
                        principal,
                        "EXEC dbo.usp_ReadManagedCoupon ?, ?, ?",
                        CouponAdminRepository::mapCoupon,
                        couponId,
                        principal.actorId(),
                        principal.authVersion()));
    }

    public UUID createCoupon(
            UserPrincipal principal, UUID organizationId, CreateCouponRequest request) {
        UUID id =
                call(
                        principal,
                        "EXEC dbo.usp_CreateManagedCoupon ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?",
                        rows -> UUID.fromString(rows.getString("id")),
                        organizationId,
                        principal.actorId(),
                        principal.authVersion(),
                        request.code(),
                        request.discountType(),
                        orNull(request.percentageValue(), Types.DECIMAL),
                        orNull(request.fixedAmount(), Types.DECIMAL),
                        orNull(request.maxDiscountAmount(), Types.DECIMAL),
                        request.maxUses(),
                        request.validFrom(),
                        request.validTo());
        if (id == null) throw new IllegalStateException("Coupon creation returned no identifier");
        return id;
    }

    public void updateCoupon(UserPrincipal principal, UUID couponId, UpdateCouponRequest request) {
        call(
                principal,
                "EXEC dbo.usp_UpdateManagedCoupon ?, ?, ?, ?, ?, ?",
                null,
                couponId,
                principal.actorId(),
                principal.authVersion(),
                orNull(request.maxUses(), Types.INTEGER),
                orNull(request.validTo(), Types.TIMESTAMP),
                orNull(request.maxDiscountAmount(), Types.DECIMAL));
    }

    public void setActive(UserPrincipal principal, UUID couponId, boolean active) {
        call(
                principal,
                "EXEC dbo.usp_SetManagedCouponActive ?, ?, ?, ?",
                null,
                couponId,
                principal.actorId(),
                principal.authVersion(),
                active);
    }

    public void deleteCoupon(UserPrincipal principal, UUID couponId) {
        call(
                principal,
                "EXEC dbo.usp_DeleteManagedCoupon ?, ?, ?",
                null,
                couponId,
                principal.actorId(),
                principal.authVersion());
    }

    /** One call on the caller's principal pool; answers with the first row mapped, or null without one. */
    private <T> T call(
            UserPrincipal principal, String sql, SqlCall.ResultSetExtractor<T> read, Object... args) {
        return SqlCall.onConnection(
                source(principal), connection -> SqlCall.call(connection, sql, read, ERRORS, args), ERRORS);
    }

    private static CouponAdminDto mapCoupon(ResultSet rows) throws SQLException {
        return new CouponAdminDto(
                UUID.fromString(rows.getString("id")),
                UUID.fromString(rows.getString("organization_id")),
                rows.getString("org_name"),
                rows.getString("code"),
                rows.getString("discount_type"),
                rows.getBigDecimal("percentage_value"),
                rows.getBigDecimal("fixed_amount"),
                rows.getBigDecimal("max_discount_amount"),
                rows.getInt("max_uses"),
                rows.getInt("reserved_uses"),
                rows.getInt("consumed_uses"),
                rows.getInt("available_uses"),
                instant(rows, "valid_from"),
                instant(rows, "valid_to"),
                rows.getBoolean("active"),
                rows.getLong("version"));
    }
}
