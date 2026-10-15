package vn.ticketscenter.identity.repository;

import org.hibernate.Session;
import org.hibernate.jdbc.ReturningWork;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Writes of the organization request lifecycle. Submission and the administrator's decision (status change,
 * manager membership, commission rule, initial event, audit log) are each one database procedure call.
 */
public class OrganizationDecisionRepository {

    public record Approval(UUID managerId, UUID commissionRuleId, UUID eventId) { }

    public record InitialEvent(String title, String venue, Instant startTime, Instant endTime) { }

    public record CommissionPolicy(BigDecimal ratePercent, Long fixedFee,
                                   Instant effectiveFrom, Instant effectiveTo) { }

    /** Creates the PENDING_APPROVAL organization for a requester and returns its id. */
    public UUID submit(TransactionManager transactions, UUID requesterId, String name, String contactEmail,
                       String contactPhone, String description) {
        return inSession(transactions, DatabasePrincipal.BUYER, connection -> {
            try (CallableStatement call = connection.prepareCall("{call dbo.usp_OrganizationSubmit(?,?,?,?,?)}")) {
                call.setString(1, requesterId.toString());
                call.setNString(2, name);
                call.setNString(3, contactEmail);
                call.setNString(4, contactPhone);
                call.setNString(5, description);
                try (ResultSet row = call.executeQuery()) {
                    if (!row.next()) throw new IllegalStateException("Organization submission returned no result");
                    return uuid(row.getString("organization_id"));
                }
            } catch (SQLException failure) {
                throw translate(failure);
            }
        });
    }

    public Approval approve(TransactionManager transactions, UUID organizationId, UUID adminId,
                            CommissionPolicy policy, InitialEvent event) {
        return inSession(transactions, DatabasePrincipal.ADMIN, connection -> {
            try (CallableStatement call = connection.prepareCall(
                    "{call dbo.sp_organization_approve(?,?,?,?,?,?,?,?,?,?)}")) {
                call.setString(1, organizationId.toString());
                call.setString(2, adminId.toString());
                if (policy == null) {
                    call.setNull(3, Types.DECIMAL);
                    call.setNull(4, Types.DECIMAL);
                    call.setNull(5, Types.TIMESTAMP);
                    call.setNull(6, Types.TIMESTAMP);
                } else {
                    call.setBigDecimal(3, policy.ratePercent());
                    call.setBigDecimal(4, policy.fixedFee() == null ? null : BigDecimal.valueOf(policy.fixedFee()));
                    call.setObject(5, utc(policy.effectiveFrom()), Types.TIMESTAMP);
                    call.setObject(6, utc(policy.effectiveTo()), Types.TIMESTAMP);
                }
                if (event == null) {
                    call.setNull(7, Types.NVARCHAR);
                    call.setNull(8, Types.NVARCHAR);
                    call.setNull(9, Types.TIMESTAMP);
                    call.setNull(10, Types.TIMESTAMP);
                } else {
                    call.setNString(7, event.title());
                    call.setNString(8, event.venue());
                    call.setObject(9, utc(event.startTime()), Types.TIMESTAMP);
                    call.setObject(10, utc(event.endTime()), Types.TIMESTAMP);
                }
                try (ResultSet row = call.executeQuery()) {
                    if (!row.next()) throw new IllegalStateException("Organization approval returned no result");
                    return new Approval(uuid(row.getString("assigned_manager_id")),
                            uuid(row.getString("commission_rule_id")), uuid(row.getString("event_id")));
                }
            } catch (SQLException failure) {
                throw translate(failure);
            }
        });
    }

    public void reject(TransactionManager transactions, UUID organizationId, UUID adminId, String reason) {
        inSession(transactions, DatabasePrincipal.ADMIN, connection -> {
            try (CallableStatement call = connection.prepareCall("{call dbo.sp_organization_reject(?,?,?)}")) {
                call.setString(1, organizationId.toString());
                call.setString(2, adminId.toString());
                call.setNString(3, reason);
                call.execute();
                return null;
            } catch (SQLException failure) {
                throw translate(failure);
            }
        });
    }

    private static <T> T inSession(TransactionManager transactions, DatabasePrincipal principal,
                                   ReturningWork<T> work) {
        return transactions.execute(principal, em -> em.unwrap(Session.class).doReturningWork(work));
    }

    /** Maps the procedures' THROW numbers onto the exceptions the service has always raised. */
    static RuntimeException translate(SQLException failure) {
        for (SQLException error = failure; error != null; error = error.getNextException()) {
            switch (error.getErrorCode()) {
                case 52620: return new IllegalArgumentException("USER_NOT_FOUND: Người dùng không tồn tại");
                case 52621: return new IllegalStateException(
                        "USER_SUSPENDED: Tài khoản của bạn hiện không thể gửi yêu cầu");
                case 52622: return new IllegalStateException(
                        "ORGANIZATION_REQUEST_EXISTS: Bạn đang có một yêu cầu tạo tổ chức đang chờ xét duyệt");
                case 52623: return new IllegalArgumentException("Tên tổ chức và email liên hệ là bắt buộc");
                case 52610: return new SecurityException(
                        "FORBIDDEN: Chỉ quản trị viên mới có quyền xử lý yêu cầu");
                case 52611: return new NoSuchElementException(
                        "ORGANIZATION_NOT_FOUND: Không tìm thấy hồ sơ yêu cầu");
                case 52612: return new IllegalStateException(
                        "CONFLICT: Yêu cầu không ở trạng thái PENDING_APPROVAL");
                case 52613: return new IllegalArgumentException("Chính sách hoa hồng ban đầu không hợp lệ");
                case 52614: return new IllegalArgumentException("Lý do từ chối phải từ 10 đến 500 ký tự");
                case 52615: return new IllegalArgumentException("Thông tin sự kiện khởi tạo không hợp lệ");
                default: break;
            }
        }
        return new IllegalStateException("Organization decision failed", failure);
    }

    private static LocalDateTime utc(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static UUID uuid(String value) {
        return value == null ? null : UUID.fromString(value);
    }
}
