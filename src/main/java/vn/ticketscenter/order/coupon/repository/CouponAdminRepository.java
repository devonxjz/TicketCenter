package vn.ticketscenter.order.coupon.repository;

import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.order.coupon.dto.CouponAdminDto;
import vn.ticketscenter.order.coupon.dto.CreateCouponRequest;
import vn.ticketscenter.order.coupon.dto.UpdateCouponRequest;
import vn.ticketscenter.order.service.OrderException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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

    private DataSource source(UserPrincipal principal) {
        return principal.isAdmin() ? admin : manager;
    }

    public boolean isManagerOrAdmin(UserPrincipal principal, UUID organizationId) {
        try (Connection connection = source(principal).getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "EXEC dbo.usp_CanManageCouponOrganization ?, ?, ?")) {
            statement.setObject(1, principal.actorId());
            statement.setInt(2, principal.authVersion());
            statement.setObject(3, organizationId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() && rows.getBoolean("allowed");
            }
        } catch (SQLException failure) {
            throw mapFailure(failure);
        }
    }

    public List<CouponAdminDto> getCouponsByOrganization(
            UserPrincipal principal, UUID organizationId, int page, int size) {
        return getAllCoupons(principal, page, size, organizationId);
    }

    public List<CouponAdminDto> getAllCoupons(
            UserPrincipal principal, int page, int size, UUID filterOrgId) {
        try (Connection connection = source(principal).getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "EXEC dbo.usp_ListManagedCoupons ?, ?, ?, ?, ?")) {
            statement.setObject(1, principal.actorId());
            statement.setInt(2, principal.authVersion());
            statement.setObject(3, filterOrgId);
            statement.setInt(4, Math.max(1, page));
            statement.setInt(5, Math.min(100, Math.max(1, size)));
            List<CouponAdminDto> result = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(mapCoupon(rows));
            }
            return result;
        } catch (SQLException failure) {
            throw mapFailure(failure);
        }
    }

    public Optional<CouponAdminDto> findById(UserPrincipal principal, UUID couponId) {
        try (Connection connection = source(principal).getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("EXEC dbo.usp_ReadManagedCoupon ?, ?, ?")) {
            statement.setObject(1, couponId);
            statement.setObject(2, principal.actorId());
            statement.setInt(3, principal.authVersion());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(mapCoupon(rows)) : Optional.empty();
            }
        } catch (SQLException failure) {
            throw mapFailure(failure);
        }
    }

    public UUID createCoupon(
            UserPrincipal principal, UUID organizationId, CreateCouponRequest request) {
        try (Connection connection = source(principal).getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "EXEC dbo.usp_CreateManagedCoupon ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?")) {
            statement.setObject(1, organizationId);
            statement.setObject(2, principal.actorId());
            statement.setInt(3, principal.authVersion());
            statement.setString(4, request.code());
            statement.setString(5, request.discountType());
            setDecimal(statement, 6, request.percentageValue());
            setDecimal(statement, 7, request.fixedAmount());
            setDecimal(statement, 8, request.maxDiscountAmount());
            statement.setInt(9, request.maxUses());
            statement.setTimestamp(10, Timestamp.from(request.validFrom()), utc());
            statement.setTimestamp(11, Timestamp.from(request.validTo()), utc());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next())
                    throw new IllegalStateException("Coupon creation returned no identifier");
                return UUID.fromString(rows.getString("id"));
            }
        } catch (SQLException failure) {
            throw mapFailure(failure);
        }
    }

    public void updateCoupon(UserPrincipal principal, UUID couponId, UpdateCouponRequest request) {
        execute(
                principal,
                "EXEC dbo.usp_UpdateManagedCoupon ?, ?, ?, ?, ?, ?",
                statement -> {
                    statement.setObject(1, couponId);
                    statement.setObject(2, principal.actorId());
                    statement.setInt(3, principal.authVersion());
                    if (request.maxUses() == null) statement.setNull(4, Types.INTEGER);
                    else statement.setInt(4, request.maxUses());
                    if (request.validTo() == null) statement.setNull(5, Types.TIMESTAMP);
                    else statement.setTimestamp(5, Timestamp.from(request.validTo()), utc());
                    setDecimal(statement, 6, request.maxDiscountAmount());
                });
    }

    public void setActive(UserPrincipal principal, UUID couponId, boolean active) {
        execute(
                principal,
                "EXEC dbo.usp_SetManagedCouponActive ?, ?, ?, ?",
                statement -> {
                    statement.setObject(1, couponId);
                    statement.setObject(2, principal.actorId());
                    statement.setInt(3, principal.authVersion());
                    statement.setBoolean(4, active);
                });
    }

    public void deleteCoupon(UserPrincipal principal, UUID couponId) {
        execute(
                principal,
                "EXEC dbo.usp_DeleteManagedCoupon ?, ?, ?",
                statement -> {
                    statement.setObject(1, couponId);
                    statement.setObject(2, principal.actorId());
                    statement.setInt(3, principal.authVersion());
                });
    }

    private void execute(UserPrincipal principal, String sql, Bind bind) {
        try (Connection connection = source(principal).getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            bind.apply(statement);
            statement.execute();
        } catch (SQLException failure) {
            throw mapFailure(failure);
        }
    }

    private static OrderException mapFailure(SQLException failure) {
        for (SQLException current = failure;
                current != null;
                current = current.getNextException()) {
            switch (current.getErrorCode()) {
                case 51520:
                    return new OrderException(
                            401, "UNAUTHENTICATED", "A current session is required.");
                case 51521:
                    return new OrderException(
                            403, "FORBIDDEN", "Coupon management permission is required.");
                case 51523:
                    return new OrderException(404, "COUPON_NOT_FOUND", "Coupon was not found.");
                case 51524:
                    return new OrderException(409, "COUPON_IN_USE", "Coupon has existing usage.");
                case 51107:
                    return new OrderException(
                            409,
                            "COUPON_USAGE_CONFLICT",
                            "Coupon max uses is below current usage.");
                case 2601, 2627:
                    return new OrderException(
                            409, "COUPON_CODE_CONFLICT", "Coupon code already exists.");
                case 547:
                    return new OrderException(
                            409, "COUPON_IN_USE", "Coupon is referenced by another record.");
                default:
                    break;
            }
        }
        return OrderException.unavailable();
    }

    private static void setDecimal(
            PreparedStatement statement, int index, java.math.BigDecimal value)
            throws SQLException {
        if (value == null) statement.setNull(index, Types.DECIMAL);
        else statement.setBigDecimal(index, value);
    }

    private static CouponAdminDto mapCoupon(ResultSet rows) throws SQLException {
        Timestamp from = rows.getTimestamp("valid_from", utc());
        Timestamp to = rows.getTimestamp("valid_to", utc());
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
                from == null ? null : from.toInstant(),
                to == null ? null : to.toInstant(),
                rows.getBoolean("active"),
                rows.getLong("version"));
    }

    @FunctionalInterface
    private interface Bind {
        void apply(PreparedStatement statement) throws SQLException;
    }

    private static java.util.Calendar utc() {
        return java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
    }
}
