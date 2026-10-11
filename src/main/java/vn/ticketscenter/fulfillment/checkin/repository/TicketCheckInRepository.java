package vn.ticketscenter.fulfillment.checkin.repository;
import org.hibernate.Session;
import vn.ticketscenter.config.persistence.*;
import vn.ticketscenter.fulfillment.checkin.CheckInException;
import vn.ticketscenter.fulfillment.checkin.dto.*;
import java.sql.*;
import java.time.*;
import java.util.*;
public class TicketCheckInRepository {
    private final TransactionManager transactions;
    public TicketCheckInRepository(TransactionManager transactions){this.transactions=transactions;}
    public CheckInScanResponseDto scan(UUID actor,UUID key,UUID event,UUID ticket,String code,String name,String fingerprint,boolean invalid){
        return work(c->{try(CallableStatement q=c.prepareCall("{call dbo.sp_ticket_check_in(?,?,?,?,?,?,?,?)}")){
            q.setString(1,event.toString());q.setString(2,actor.toString());q.setString(3,ticket==null?null:ticket.toString());
            q.setString(4,code);q.setNString(5,name);q.setString(6,key.toString());q.setString(7,fingerprint);q.setBoolean(8,invalid);q.setQueryTimeout(15);
            try(ResultSet r=q.executeQuery()){if(!r.next())throw new SQLException("Check-in result missing");
                return new CheckInScanResponseDto(r.getString("result"),id(r,"ticket_id"),r.getString("ticket_code"),r.getString("customer_name"),
                    r.getString("zone_name"),r.getString("seat_label"),instant(r,"checked_in_at"),instant(r,"scanned_at"),id(r,"organization_id"));}
        }});
    }
    public List<RecentCheckInLogDto> recent(UUID actor,UUID event,int limit){
        return work(c->{try(CallableStatement q=c.prepareCall("{call dbo.sp_checkin_recent_logs(?,?,?)}")){
            q.setString(1,event.toString());q.setString(2,actor.toString());q.setInt(3,limit);q.setQueryTimeout(5);
            try(ResultSet r=q.executeQuery()){List<RecentCheckInLogDto> list=new ArrayList<>();while(r.next())list.add(new RecentCheckInLogDto(
                id(r,"id"),id(r,"ticket_id"),r.getString("result"),instant(r,"scanned_at"),r.getString("staff_name"),r.getString("zone_name"),r.getString("seat_label")));return List.copyOf(list);}
        }});
    }
    private <T> T work(SqlWork<T> work){
        try{return transactions.execute(DatabasePrincipal.CHECK_IN,em->em.unwrap(Session.class).doReturningWork(work::run));}
        catch(RuntimeException e){for(Throwable t=e;t!=null;t=t.getCause())if(t instanceof SQLException sql){
            int n=sql.getErrorCode();if(n==53201)throw new CheckInException(403,"CHECK_IN_FORBIDDEN","Bạn không có quyền soát vé tại sự kiện này.");
            if(n==53202)throw new CheckInException(404,"EVENT_NOT_FOUND","Sự kiện không tồn tại.");
            if(n==53203)throw new CheckInException(409,"IDEMPOTENCY_CONFLICT","Mã yêu cầu đã dùng cho một thao tác khác.");
        }throw new CheckInException(503,"CHECK_IN_UNAVAILABLE","Hệ thống soát vé tạm thời không khả dụng. Hãy thử lại cùng mã yêu cầu.");}
    }
    @FunctionalInterface private interface SqlWork<T>{T run(Connection c)throws SQLException;}
    private static UUID id(ResultSet r,String column)throws SQLException{String v=r.getString(column);return v==null?null:UUID.fromString(v);}
    private static Instant instant(ResultSet r,String column)throws SQLException{
        Object v=r.getObject(column);if(v==null)return null;
        return r.getMetaData().getColumnType(r.findColumn(column))==-155?r.getObject(column,OffsetDateTime.class).toInstant():r.getObject(column,LocalDateTime.class).toInstant(ZoneOffset.UTC);
    }
}
