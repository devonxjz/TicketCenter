package vn.ticketscenter.fulfillment.refund.repository;

import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.hibernate.Session;
import vn.ticketscenter.config.persistence.*;
import vn.ticketscenter.fulfillment.refund.dto.*;
import vn.ticketscenter.fulfillment.refund.service.RefundException;
import vn.ticketscenter.identity.security.UserPrincipal;

/** JDBC projections and SP-owned writes inside the shared resource-local JPA transaction. */
public class RefundRequestRepository implements RefundRequestStore {
    private final TransactionManager transactions;
    public RefundRequestRepository(TransactionManager transactions) { this.transactions=transactions; }
    @FunctionalInterface private interface Work<T> { T run(Connection connection)throws SQLException; }
    private <T>T sql(Work<T> work) {
        try { return transactions.execute(DatabasePrincipal.BUYER,em->em.unwrap(Session.class).doReturningWork(work::run)); }
        catch(RuntimeException failure) {
            for(Throwable cause=failure;cause!=null;cause=cause.getCause()) if(cause instanceof SQLException e) {
                int status=switch(e.getErrorCode()){case 53000,53005->400;case 53001->401;case 53003->403;case 53004->404;case 53009->409;default->503;};
                String code=switch(e.getErrorCode()){case 53000->"INVALID_REQUEST";case 53005->"REFUND_NOT_ELIGIBLE";case 53001->"SESSION_EXPIRED";case 53003->"ORDER_FORBIDDEN";case 53004->"ORDER_NOT_FOUND";case 53009->"REFUND_CONFLICT";default->"DATABASE_UNAVAILABLE";};
                throw new RefundException(status,code,"Không thể thực hiện yêu cầu hoàn vé. Vui lòng kiểm tra quyền, thời hạn và trạng thái vé.");
            }
            throw failure;
        }
    }
    private static PreparedStatement query(Connection c,String sql)throws SQLException { var q=c.prepareStatement(sql);q.setQueryTimeout(10);return q; }
    @Override public boolean active(UserPrincipal actor) {
        return sql(c->{try(var q=query(c,"SELECT 1 FROM dbo.vw_ActorAuthority WHERE id=? AND status='ACTIVE' AND auth_version=?")){
            q.setString(1,actor.actorId().toString());q.setLong(2,actor.authVersion());try(var r=q.executeQuery()){return r.next();}
        }});
    }
    private static void owner(Connection c,UserPrincipal actor,UUID order)throws SQLException {
        try(var q=query(c,"SELECT owner_id FROM dbo.v_refundable_orders WHERE order_id=?")){
            q.setString(1,order.toString());try(var r=q.executeQuery()){
                if(!r.next())throw new RefundException(404,"ORDER_NOT_FOUND","Đơn hàng không tồn tại.");
                if(!actor.actorId().toString().equalsIgnoreCase(r.getString(1)))throw new RefundException(403,"ORDER_FORBIDDEN","Bạn không có quyền xem đơn hàng này.");
            }
        }
    }
    @Override public List<RefundTicketOptionDto> tickets(UserPrincipal actor,UUID order,Instant ignoredClock) {
        return sql(c->{owner(c,actor,order);try(var q=query(c,"""
          SELECT t.*,CASE WHEN t.has_open_refund=1 OR t.status='REFUND_PENDING' THEN N'Vé đang chờ xử lý hoàn tiền.'
           WHEN t.status='USED' THEN N'Vé đã được sử dụng.' WHEN t.status<>'ACTIVE' THEN N'Vé không còn hiệu lực.'
           WHEN o.order_status<>'PAID' THEN N'Đơn hàng chưa thanh toán.' WHEN o.event_status<>'PUBLISHED' THEN N'Sự kiện không còn công khai.'
           WHEN SYSUTCDATETIME()>=o.start_time THEN N'Đã quá thời hạn yêu cầu hoàn vé.' ELSE NULL END unrefundable_reason
          FROM dbo.v_refundable_ticket_options t JOIN dbo.v_refundable_orders o ON o.order_id=t.order_id
          WHERE t.order_id=? ORDER BY t.ticket_code,t.ticket_id
          """)){q.setString(1,order.toString());try(var r=q.executeQuery()){var result=new ArrayList<RefundTicketOptionDto>();while(r.next())result.add(ticket(r,r.getString("unrefundable_reason")));return List.copyOf(result);}}});
    }
    @Override public List<OrderOption> orders(UserPrincipal actor) {
        return sql(c->{try(var q=query(c,"SELECT TOP(100) order_id,order_code,event_name FROM dbo.v_refundable_orders WHERE owner_id=? AND order_status='PAID' ORDER BY start_time DESC,order_id")){
            q.setString(1,actor.actorId().toString());try(var r=q.executeQuery()){var result=new ArrayList<OrderOption>();while(r.next())result.add(new OrderOption(UUID.fromString(r.getString(1)),r.getString(2),r.getString(3)));return List.copyOf(result);}
        }});
    }
    @Override public RefundSummaryDto create(UserPrincipal actor,CreateRefundRequestDto request,UUID key) {
        return sql(c->{try(var call=c.prepareCall("{call dbo.sp_refund_request_for_tickets(?,?,?,?,?,?,?)}")){
            call.setQueryTimeout(15);call.setString(1,request.orderId().toString());call.setString(2,actor.actorId().toString());
            call.setString(3,request.ticketIds().stream().map(UUID::toString).collect(Collectors.joining(",")));call.setString(4,request.reason());
            call.registerOutParameter(5,Types.VARCHAR);call.setString(6,key.toString());call.setLong(7,actor.authVersion());
            boolean result=call.execute();while(!result&&call.getUpdateCount()!=-1)result=call.getMoreResults();
            if(!result)throw new SQLException("Missing refund result");try(var rows=call.getResultSet()){if(!rows.next())throw new SQLException("Missing refund row");return summary(rows);}
        }});
    }
    @Override public RefundPageDto list(UserPrincipal actor,int page,int size) {
        return sql(c->{long count;try(var q=query(c,"SELECT COUNT_BIG(*) FROM dbo.v_refunds WHERE owner_id=?")){q.setString(1,actor.actorId().toString());try(var r=q.executeQuery()){r.next();count=r.getLong(1);}}
            try(var q=query(c,"SELECT * FROM dbo.v_refunds WHERE owner_id=? ORDER BY created_at DESC,refund_id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY")){
                q.setString(1,actor.actorId().toString());q.setInt(2,(page-1)*size);q.setInt(3,size);try(var r=q.executeQuery()){var items=new ArrayList<RefundSummaryDto>();while(r.next())items.add(summary(r));return new RefundPageDto(items,count,page,size);}
            }
        });
    }
    @Override public RefundDetailDto detail(UserPrincipal actor,UUID id) {
        return sql(c->{RefundSummaryDto refund;UUID attempt;Instant processed;
            try(var q=query(c,"SELECT * FROM dbo.v_refunds WHERE refund_id=?")){q.setString(1,id.toString());try(var r=q.executeQuery()){
                if(!r.next())throw new RefundException(404,"REFUND_NOT_FOUND","Yêu cầu hoàn tiền không tồn tại.");
                if(!actor.actorId().toString().equalsIgnoreCase(r.getString("owner_id")))throw new RefundException(403,"REFUND_FORBIDDEN","Bạn không có quyền xem yêu cầu này.");
                refund=summary(r);String raw=r.getString("current_attempt_id");attempt=raw==null?null:UUID.fromString(raw);processed=instant(r,"processed_at");
            }}
            var tickets=new ArrayList<RefundTicketOptionDto>();try(var q=query(c,"SELECT * FROM dbo.v_refund_tickets WHERE refund_id=? ORDER BY ticket_id")){q.setString(1,id.toString());try(var r=q.executeQuery()){while(r.next())tickets.add(ticket(r,"Vé thuộc yêu cầu hoàn tiền này."));}}
            var attempts=new ArrayList<RefundDetailDto.Attempt>();try(var q=query(c,"SELECT * FROM dbo.v_refund_attempts WHERE refund_id=? ORDER BY attempt_no")){q.setString(1,id.toString());try(var r=q.executeQuery()){while(r.next())attempts.add(new RefundDetailDto.Attempt(UUID.fromString(r.getString("attempt_id")),r.getInt("attempt_no"),r.getString("status"),instant(r,"created_at"),instant(r,"processed_at")));}}
            return new RefundDetailDto(refund,attempt,processed,tickets,attempts);
        });
    }
    private static RefundSummaryDto summary(ResultSet r)throws SQLException {return new RefundSummaryDto(UUID.fromString(r.getString("refund_id")),UUID.fromString(r.getString("order_id")),r.getBigDecimal("amount").toPlainString(),r.getString("status"),r.getInt("ticket_count"),instant(r,"created_at"),r.getString("event_name"),r.getString("reason"),r.getString("rejection_reason"));}
    private static RefundTicketOptionDto ticket(ResultSet r,String reason)throws SQLException {String seat=r.getString("seat_label_snapshot");return new RefundTicketOptionDto(UUID.fromString(r.getString("ticket_id")),r.getString("ticket_code"),seat==null?r.getString("zone_name_snapshot"):seat,r.getBigDecimal("paid_amount").toPlainString(),r.getString("status"),reason==null,reason);}
    private static Instant instant(ResultSet r,String column)throws SQLException {Timestamp v=r.getTimestamp(column,Calendar.getInstance(TimeZone.getTimeZone("UTC")));return v==null?null:v.toInstant();}
}
