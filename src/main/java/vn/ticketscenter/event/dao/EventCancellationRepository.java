package vn.ticketscenter.event.dao;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import vn.ticketscenter.config.persistence.*;
import vn.ticketscenter.event.cancellation.dto.*;
import vn.ticketscenter.event.cancellation.service.EventCancellationStore;
import vn.ticketscenter.fulfillment.refund.service.RefundException;
import vn.ticketscenter.identity.security.UserPrincipal;

public class EventCancellationRepository implements EventCancellationStore {
    public record Work(UUID eventId, UUID orderId) { }
    private final TransactionManager transactions;
    private final DataSource supplied;
    public EventCancellationRepository() { transactions=null; supplied=null; }
    public EventCancellationRepository(TransactionManager transactions) { this.transactions=Objects.requireNonNull(transactions); supplied=null; }
    public EventCancellationRepository(DataSource source) { supplied=Objects.requireNonNull(source); transactions=null; }
    @FunctionalInterface private interface SqlWork<T> { T run(Connection connection) throws SQLException; }
    private <T> T admin(SqlWork<T> work) {
        try {
            if (transactions!=null) return transactions.execute(DatabasePrincipal.ADMIN,
                    em -> em.unwrap(Session.class).doReturningWork(work::run));
            try (var connection=supplied.getConnection()) {
                connection.setAutoCommit(false);
                try { var result=work.run(connection); connection.commit(); return result; }
                catch (RuntimeException|SQLException e) { connection.rollback(); throw e; }
            }
        } catch (Exception failure) { throw translated(failure); }
    }
    private static RuntimeException translated(Exception failure) {
        if (failure instanceof RefundException e) return e;
        for (Throwable cause=failure;cause!=null;cause=cause.getCause()) if (cause instanceof SQLException e) {
            int status=switch(e.getErrorCode()) { case 54200 -> 400; case 54201 -> 401; case 54203 -> 403; case 54204 -> 404; case 54209 -> 409; default -> 503; };
            String code=switch(status) {case 400 -> "EVENT_CANNOT_BE_CANCELLED";case 401 -> "SESSION_EXPIRED";case 403 -> "ADMIN_REQUIRED";case 404 -> "CANCELLATION_NOT_FOUND";case 409 -> "CANCELLATION_CONFLICT";default -> "DATABASE_UNAVAILABLE";};
            return new RefundException(status,code,"Không thể xử lý hủy sự kiện. Hãy kiểm tra quyền, thời gian và trạng thái sự kiện.");
        }
        return new RefundException(503,"DATABASE_UNAVAILABLE","Hệ thống hủy sự kiện tạm thời không khả dụng.");
    }
    private static PreparedStatement query(Connection c,String sql,Object...args) throws SQLException {
        var q=c.prepareStatement(sql);q.setQueryTimeout(20);
        for(int i=0;i<args.length;i++) q.setObject(i+1,args[i] instanceof UUID id?id.toString():args[i]);
        return q;
    }
    private static void authority(Connection c,UserPrincipal actor) throws SQLException {
        try(var q=query(c,"SELECT status,auth_version,is_admin FROM dbo.v_admin_refund_actor WHERE id=?",actor.actorId());var r=q.executeQuery()) {
            if(!r.next()||!"ACTIVE".equals(r.getString(1))||r.getInt(2)!=actor.authVersion())
                throw new RefundException(401,"SESSION_EXPIRED","Phiên đăng nhập đã hết hạn.");
            if(!r.getBoolean(3)) throw new RefundException(403,"ADMIN_REQUIRED","Bạn không còn quyền quản trị viên.");
        }
    }
    @Override public void requireAdmin(UserPrincipal actor) { admin(c->{authority(c,actor);return null;}); }
    @Override public CancellationProgressDto cancel(UserPrincipal actor,UUID id,EventCancelRequestDto request,UUID key) {
        if(transactions!=null)try {
            return transactions.execute(DatabasePrincipal.ADMIN,em->{
                var procedure=em.createStoredProcedureQuery("dbo.sp_event_cancel");
                for(String name:List.of("event_id","admin_user_id","cancel_reason","idempotency_key"))
                    procedure.registerStoredProcedureParameter(name,String.class,jakarta.persistence.ParameterMode.IN);
                procedure.registerStoredProcedureParameter("auth_version",Integer.class,jakarta.persistence.ParameterMode.IN)
                        .setParameter("event_id",id.toString()).setParameter("admin_user_id",actor.actorId().toString())
                        .setParameter("cancel_reason",request.reason()).setParameter("idempotency_key",key.toString())
                        .setParameter("auth_version",actor.authVersion());
                consume(procedure);em.clear();
                return em.unwrap(Session.class).doReturningWork(c->{
                    try(var q=query(c,"SELECT * FROM dbo.v_admin_cancellation_progress WHERE event_id=?",id);var r=q.executeQuery()) {
                        if(!r.next())throw new SQLException("Missing cancellation projection");return projection(r);
                    }
                });
            });
        }catch(Exception failure){throw translated(failure);}
        return admin(c->{try(var q=query(c,"EXEC dbo.sp_event_cancel ?,?,?,?,?",id,actor.actorId(),request.reason(),key,actor.authVersion())) {
            boolean result=q.execute();while(!result&&q.getUpdateCount()!=-1) result=q.getMoreResults();
            if(!result) throw new SQLException("Missing cancellation projection");
            try(var r=q.getResultSet()) {if(!r.next())throw new SQLException("Missing cancellation row");return projection(r);}
        }});
    }
    @Override public CancellationProgressDto progress(UserPrincipal actor,UUID id) {
        return admin(c->{authority(c,actor);
            try(var q=query(c,"SELECT * FROM dbo.v_admin_cancellation_progress WHERE event_id=?",id);var r=q.executeQuery()) {
                if(!r.next()) throw new RefundException(404,"CANCELLATION_NOT_FOUND","Sự kiện chưa có tác vụ hủy.");
                return projection(r);
            }
        });
    }
    private static UUID uuid(ResultSet r,String name)throws SQLException {var value=r.getString(name);return value==null?null:UUID.fromString(value);}
    private static Instant instant(ResultSet r,String name)throws SQLException {var value=r.getTimestamp(name,Calendar.getInstance(TimeZone.getTimeZone("UTC")));return value.toInstant();}
    private static CancellationProgressDto projection(ResultSet r)throws SQLException {
        return new CancellationProgressDto(uuid(r,"event_id"),uuid(r,"organization_id"),r.getString("status"),r.getString("reason"),
                instant(r,"cancelled_at"),r.getLong("total_orders"),r.getLong("processed_orders"),uuid(r,"last_processed_order_id"),
                instant(r,"updated_at"),r.getLong("successful_refunds"),r.getLong("failed_refunds"),r.getLong("unknown_refunds"),
                r.getLong("pending_refunds"),r.getLong("used_ticket_exceptions"));
    }
    public List<Object> findWork(EntityManager entityManager, int batchSize) {
        if(batchSize<1||batchSize>1000)throw new IllegalArgumentException("Invalid cancellation batch size");
        return entityManager.unwrap(Session.class).doReturningWork(c->{
            var work=new ArrayList<Object>();
            try(var q=query(c,"SELECT TOP(?) event_id,order_id FROM dbo.v_worker_cancellation_orders ORDER BY cancelled_at,event_id,order_id",batchSize);var r=q.executeQuery()) {
                while(r.next())work.add(new Work(uuid(r,"event_id"),uuid(r,"order_id")));
            }return work;
        });
    }

    public void process(EntityManager entityManager, Object item) {
        var work=(Work)item;
        var procedure=entityManager.createStoredProcedureQuery("dbo.sp_refund_adopt_event_cancellation")
                .registerStoredProcedureParameter("event_id",String.class,jakarta.persistence.ParameterMode.IN)
                .registerStoredProcedureParameter("order_id",String.class,jakarta.persistence.ParameterMode.IN)
                .setParameter("event_id",work.eventId().toString()).setParameter("order_id",work.orderId().toString());
        consume(procedure);
        entityManager.clear();
    }
    private static void consume(jakarta.persistence.StoredProcedureQuery procedure) {
        boolean result=procedure.execute();
        // SP17 calls SP10, which returns intermediate projections. Consume ALL results so a
        // failure after adoption/cursor mutation is observed before the caller commits.
        while(true) {
            if(result)procedure.getResultList();
            else if(procedure.getUpdateCount()==-1)break;
            result=procedure.hasMoreResults();
        }
    }

    public void completeFinishedEvents(EntityManager entityManager) {
        entityManager.createStoredProcedureQuery("dbo.sp_event_cancellation_finish").execute();
        entityManager.clear();
    }
}
