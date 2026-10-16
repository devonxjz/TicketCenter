package vn.ticketscenter.fulfillment.refund.dao;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.hibernate.Session;
import vn.ticketscenter.config.persistence.*;
import vn.ticketscenter.fulfillment.refund.dto.*;
import vn.ticketscenter.fulfillment.refund.service.RefundException;
import vn.ticketscenter.identity.security.UserPrincipal;

/** Shared resource-local transaction in production; DataSource constructor enables real isolated fixtures. */
public final class AdminRefundRepository implements AdminRefundStore {
    private final TransactionManager transactions;
    private final DataSource supplied;
    public AdminRefundRepository(TransactionManager transactions) { this.transactions=Objects.requireNonNull(transactions);supplied=null; }
    public AdminRefundRepository(DataSource supplied) { this.supplied=Objects.requireNonNull(supplied);transactions=null; }
    @FunctionalInterface private interface Work<T> { T run(Connection c)throws SQLException; }
    private <T>T sql(Work<T> work) {
        try {
            if(transactions!=null)return transactions.execute(DatabasePrincipal.ADMIN,em->em.unwrap(Session.class).doReturningWork(work::run));
            try(var c=supplied.getConnection()) {
                c.setAutoCommit(false);
                try {T value=work.run(c);c.commit();return value;}catch(SQLException|RuntimeException failure){c.rollback();throw failure;}
            }
        }catch(Exception failure) {
            if(failure instanceof RefundException e)throw e;
            for(Throwable cause=failure;cause!=null;cause=cause.getCause())if(cause instanceof SQLException e) {
                int status=switch(e.getErrorCode()) {case 54100,51400,51405->400;case 54101->401;case 54103,51402->403;case 54104,51401->404;case 54109,51403,51404,51406,51407,51805,51806->409;default->503;};
                String code=switch(status) {case 400->"REFUND_DECISION_INVALID";case 401->"SESSION_EXPIRED";case 403->"ADMIN_REQUIRED";case 404->"REFUND_NOT_FOUND";case 409->"REFUND_DECISION_CONFLICT";default->"DATABASE_UNAVAILABLE";};
                throw new RefundException(status,code,status==409?"Trạng thái hoàn tiền đã thay đổi hoặc cần đối soát. Hãy tải lại hồ sơ.":"Không thể xử lý hồ sơ hoàn tiền. Vui lòng kiểm tra quyền và trạng thái.");
            }
            throw new RefundException(503,"DATABASE_UNAVAILABLE","Hệ thống hoàn tiền tạm thời không khả dụng.");
        }
    }
    private static PreparedStatement query(Connection c,String sql,Object...args)throws SQLException {
        var q=c.prepareStatement(sql);q.setQueryTimeout(15);
        for(int i=0;i<args.length;i++)q.setObject(i+1,args[i] instanceof UUID id?id.toString():args[i]);return q;
    }
    @Override public void requireAdmin(UserPrincipal actor) {
        sql(c->{try(var q=query(c,"SELECT status,auth_version,is_admin FROM dbo.v_admin_refund_actor WHERE id=?",actor.actorId());var r=q.executeQuery()) {
            if(!r.next()||!"ACTIVE".equals(r.getString(1))||r.getInt(2)!=actor.authVersion())throw new RefundException(401,"SESSION_EXPIRED","Phiên đăng nhập đã hết hạn.");
            if(!r.getBoolean(3))throw new RefundException(403,"ADMIN_REQUIRED","Bạn không còn quyền quản trị viên.");
            return null;
        }});
    }
    @Override public AdminRefundPageDto list(String status,int page,int size) {
        return sql(c->{String where=status==null?"":" WHERE status=?";long total;
            try(var q=query(c,"SELECT COUNT_BIG(*) FROM dbo.v_admin_refund_requests"+where,status==null?new Object[]{}:new Object[]{status});var r=q.executeQuery()){r.next();total=r.getLong(1);}
            Object[] params=status==null?new Object[]{(page-1)*size,size}:new Object[]{status,(page-1)*size,size};
            try(var q=query(c,"SELECT * FROM dbo.v_admin_refund_requests"+where+" ORDER BY created_at DESC,refund_id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",params);var r=q.executeQuery()){
                var items=new ArrayList<AdminRefundSummaryDto>();while(r.next())items.add(summary(r));return new AdminRefundPageDto(items,total,page,size);
            }
        });
    }
    @Override public AdminRefundDetailDto detail(UUID id) {
        return sql(c->{AdminRefundSummaryDto refund;UUID current,reviewer;Instant decided;
            try(var q=query(c,"SELECT * FROM dbo.v_admin_refund_requests WHERE refund_id=?",id);var r=q.executeQuery()){
                if(!r.next())throw new RefundException(404,"REFUND_NOT_FOUND","Yêu cầu hoàn tiền không tồn tại.");
                refund=summary(r);current=uuid(r,"current_attempt_id");reviewer=uuid(r,"reviewer_id");decided=instant(r,"decided_at");
            }
            var tickets=new ArrayList<RefundTicketOptionDto>();
            try(var q=query(c,"SELECT * FROM dbo.v_refund_tickets WHERE refund_id=? ORDER BY ticket_id",id);var r=q.executeQuery()){
                while(r.next())tickets.add(new RefundTicketOptionDto(uuid(r,"ticket_id"),r.getString("ticket_code"),r.getString("seat_label_snapshot")==null?r.getString("zone_name_snapshot"):r.getString("seat_label_snapshot"),r.getBigDecimal("paid_amount").toPlainString(),r.getString("status"),false,null));
            }
            var attempts=new ArrayList<AdminRefundDetailDto.Attempt>();
            try(var q=query(c,"SELECT * FROM dbo.v_admin_refund_attempts WHERE refund_id=? ORDER BY attempt_no,attempt_id",id);var r=q.executeQuery()){
                while(r.next())attempts.add(new AdminRefundDetailDto.Attempt(uuid(r,"attempt_id"),r.getInt("attempt_no"),r.getString("status"),r.getString("provider_reference"),instant(r,"created_at"),instant(r,"processed_at")));
            }
            String note=null;
            try(var q=query(c,"SELECT TOP(1) note FROM dbo.vw_AdminRefundApprovalNotes WHERE refund_id=? ORDER BY created_at DESC,id DESC",id);var r=q.executeQuery()){if(r.next())note=r.getString(1);}
            return new AdminRefundDetailDto(refund,current,reviewer,decided,note,tickets,attempts);
        });
    }
    @Override public AdminRefundMutationDto decide(UserPrincipal actor,UUID id,AdminRefundDecisionRequestDto request,UUID key) {
        return sql(c->{String proc="APPROVE".equals(request.decision())?"sp_refund_approve":"sp_refund_reject";
            try(var q=query(c,"EXEC dbo."+proc+" ?,?,?,?,?",id,actor.actorId(),"APPROVE".equals(request.decision())?request.note():request.rejectReason(),key,actor.authVersion())){return mutation(q);}
        });
    }
    @Override public AdminRefundMutationDto retry(UserPrincipal actor,UUID id,UUID key) {
        return sql(c->{try(var q=query(c,"EXEC dbo.sp_refund_retry ?,?,?,?",id,actor.actorId(),key,actor.authVersion())){return mutation(q);}});
    }
    private static AdminRefundMutationDto mutation(PreparedStatement q)throws SQLException {
        boolean result=q.execute();while(!result&&q.getUpdateCount()!=-1)result=q.getMoreResults();
        if(!result)throw new SQLException("Missing mutation projection");
        try(var r=q.getResultSet()) {if(!r.next())throw new SQLException("Missing mutation row");String csv=r.getString("ticket_ids");
            return new AdminRefundMutationDto(uuid(r,"refund_id"),r.getString("status"),r.getString("canonical_status"),uuid(r,"attempt_id"),uuid(r,"buyer_id"),csv==null?List.of():Arrays.stream(csv.split(",")).map(UUID::fromString).toList());
        }
    }
    private static AdminRefundSummaryDto summary(ResultSet r)throws SQLException {return new AdminRefundSummaryDto(uuid(r,"refund_id"),uuid(r,"order_id"),uuid(r,"buyer_id"),r.getString("buyer_name"),r.getString("buyer_email"),r.getString("event_name"),r.getBigDecimal("amount").toPlainString(),r.getInt("ticket_count"),r.getString("reason"),r.getString("status"),instant(r,"created_at"),r.getString("rejection_reason"),r.getBoolean("can_decide"),r.getBoolean("can_retry"));}
    private static UUID uuid(ResultSet r,String column)throws SQLException {String value=r.getString(column);return value==null?null:UUID.fromString(value);}
    private static Instant instant(ResultSet r,String column)throws SQLException {var value=r.getTimestamp(column,Calendar.getInstance(TimeZone.getTimeZone("UTC")));return value==null?null:value.toInstant();}
}
