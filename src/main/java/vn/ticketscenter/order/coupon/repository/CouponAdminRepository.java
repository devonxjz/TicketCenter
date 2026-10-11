package vn.ticketscenter.order.coupon.repository;

import vn.ticketscenter.order.coupon.dto.CouponAdminDto;
import vn.ticketscenter.order.coupon.dto.CreateCouponRequest;
import vn.ticketscenter.order.coupon.dto.UpdateCouponRequest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * Kho dữ liệu quản trị Coupon kết nối SQL Server (LIEM-06 / UI-14).
 */
public class CouponAdminRepository {
    private final DataSource dataSource;

    public CouponAdminRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    protected CouponAdminRepository() {
        this.dataSource = null;
    }

    public boolean isManagerOrAdmin(UUID actorId, boolean isAdmin, UUID organizationId) {
        if (isAdmin) return true;
        if (actorId == null || organizationId == null || dataSource == null) return false;

        String sql = """
                SELECT 1 FROM dbo.tc_organization_memberships WITH (NOLOCK)
                WHERE user_id = ? AND organization_id = ? AND role = 'MANAGER' AND active = 1
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, actorId);
            ps.setObject(2, organizationId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public List<CouponAdminDto> getCouponsByOrganization(UUID organizationId, int page, int size) {
        return getAllCoupons(page, size, organizationId);
    }

    public List<CouponAdminDto> getAllCoupons(int page, int size, UUID filterOrgId) {
        if (dataSource == null) return List.of();
        int targetPage = Math.max(1, page);
        int targetSize = Math.min(100, Math.max(1, size));
        int offset = (targetPage - 1) * targetSize;

        String sql = """
                SELECT c.id, c.organization_id, o.name as org_name, c.code, c.discount_type,
                       c.percentage_value, c.fixed_amount, c.max_discount_amount,
                       c.max_uses, u.reserved_uses, u.consumed_uses, u.available_uses,
                       c.valid_from, c.valid_to, c.active, c.version
                FROM dbo.tc_coupons c WITH (NOLOCK)
                JOIN dbo.vw_CouponUsage u WITH (NOLOCK) ON u.coupon_id = c.id
                LEFT JOIN dbo.tc_organizations o WITH (NOLOCK) ON o.id = c.organization_id
                WHERE (? IS NULL OR c.organization_id = ?)
                ORDER BY c.valid_from DESC
                OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """;

        List<CouponAdminDto> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, filterOrgId);
            ps.setObject(2, filterOrgId);
            ps.setInt(3, offset);
            ps.setInt(4, targetSize);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapCoupon(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to query coupons", e);
        }
        return list;
    }

    public Optional<CouponAdminDto> findById(UUID couponId) {
        if (dataSource == null || couponId == null) return Optional.empty();
        String sql = """
                SELECT c.id, c.organization_id, o.name as org_name, c.code, c.discount_type,
                       c.percentage_value, c.fixed_amount, c.max_discount_amount,
                       c.max_uses, u.reserved_uses, u.consumed_uses, u.available_uses,
                       c.valid_from, c.valid_to, c.active, c.version
                FROM dbo.tc_coupons c WITH (NOLOCK)
                JOIN dbo.vw_CouponUsage u WITH (NOLOCK) ON u.coupon_id = c.id
                LEFT JOIN dbo.tc_organizations o WITH (NOLOCK) ON o.id = c.organization_id
                WHERE c.id = ?
                """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, couponId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapCoupon(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find coupon by id", e);
        }
        return Optional.empty();
    }

    public UUID createCoupon(UUID organizationId, CreateCouponRequest req) {
        Objects.requireNonNull(organizationId, "organizationId is required");
        Objects.requireNonNull(req, "request is required");

        UUID id = UUID.randomUUID();
        String sql = """
                INSERT INTO dbo.tc_coupons (id, organization_id, code, discount_type, percentage_value,
                                            fixed_amount, max_discount_amount, max_uses, valid_from, valid_to, active, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 0)
                """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, organizationId);
            ps.setString(3, req.code());
            ps.setString(4, req.discountType());
            ps.setBigDecimal(5, req.percentageValue());
            ps.setBigDecimal(6, req.fixedAmount());
            ps.setBigDecimal(7, req.maxDiscountAmount());
            ps.setInt(8, req.maxUses());
            ps.setTimestamp(9, Timestamp.from(req.validFrom()));
            ps.setTimestamp(10, Timestamp.from(req.validTo()));

            ps.executeUpdate();
            return id;
        } catch (SQLException e) {
            if (e.getErrorCode() == 2627 || e.getErrorCode() == 2601) {
                throw new IllegalArgumentException("Coupon code already exists in this organization", e);
            }
            throw new IllegalStateException("Failed to insert coupon", e);
        }
    }

    public void updateCoupon(UUID couponId, UpdateCouponRequest req) {
        Objects.requireNonNull(couponId, "couponId is required");
        String sql = """
                UPDATE dbo.tc_coupons
                SET max_uses = COALESCE(?, max_uses),
                    valid_to = COALESCE(?, valid_to),
                    max_discount_amount = COALESCE(?, max_discount_amount),
                    version = version + 1
                WHERE id = ?
                """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (req.maxUses() != null) ps.setInt(1, req.maxUses());
            else ps.setNull(1, Types.INTEGER);

            if (req.validTo() != null) ps.setTimestamp(2, Timestamp.from(req.validTo()));
            else ps.setNull(2, Types.TIMESTAMP);

            if (req.maxDiscountAmount() != null) ps.setBigDecimal(3, req.maxDiscountAmount());
            else ps.setNull(3, Types.DECIMAL);

            ps.setObject(4, couponId);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() == 51107) {
                throw new IllegalArgumentException("Coupon max uses is below current usage", e);
            }
            throw new IllegalStateException("Failed to update coupon", e);
        }
    }

    public void setActive(UUID couponId, boolean active) {
        Objects.requireNonNull(couponId, "couponId is required");
        String sql = "UPDATE dbo.tc_coupons SET active = ?, version = version + 1 WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, active);
            ps.setObject(2, couponId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to toggle coupon status", e);
        }
    }

    public void deleteCoupon(UUID couponId) {
        Objects.requireNonNull(couponId, "couponId is required");

        // Kiểm tra xem đã có redemption hoặc order chưa
        String checkRedemptionsSql = "SELECT COUNT_BIG(*) FROM dbo.tc_coupon_redemptions WITH (NOLOCK) WHERE coupon_id = ?";
        String checkOrdersSql = "SELECT COUNT_BIG(*) FROM dbo.tc_orders WITH (NOLOCK) WHERE coupon_id = ?";
        String deleteSql = "DELETE FROM dbo.tc_coupons WHERE id = ?";

        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(checkRedemptionsSql)) {
                ps.setObject(1, couponId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getLong(1) > 0) {
                        throw new IllegalStateException("Cannot delete coupon with existing redemptions");
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(checkOrdersSql)) {
                ps.setObject(1, couponId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getLong(1) > 0) {
                        throw new IllegalStateException("Cannot delete coupon applied to orders");
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setObject(1, couponId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete coupon", e);
        }
    }

    private static CouponAdminDto mapCoupon(ResultSet rs) throws SQLException {
        Timestamp vf = rs.getTimestamp("valid_from");
        Timestamp vt = rs.getTimestamp("valid_to");
        return new CouponAdminDto(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("organization_id")),
                rs.getString("org_name"),
                rs.getString("code"),
                rs.getString("discount_type"),
                rs.getBigDecimal("percentage_value"),
                rs.getBigDecimal("fixed_amount"),
                rs.getBigDecimal("max_discount_amount"),
                rs.getInt("max_uses"),
                rs.getInt("reserved_uses"),
                rs.getInt("consumed_uses"),
                rs.getInt("available_uses"),
                vf != null ? vf.toInstant() : null,
                vt != null ? vt.toInstant() : null,
                rs.getBoolean("active"),
                rs.getLong("version")
        );
    }
}
