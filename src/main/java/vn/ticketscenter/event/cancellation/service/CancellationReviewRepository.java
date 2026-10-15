package vn.ticketscenter.event.cancellation.service;

import java.sql.SQLException;
import java.util.UUID;
import org.hibernate.Session;
import vn.ticketscenter.config.persistence.*;
import vn.ticketscenter.fulfillment.refund.service.RefundException;
import vn.ticketscenter.identity.security.UserPrincipal;

public final class CancellationReviewRepository implements CancellationReviewService.Store {
    private final TransactionManager transactions;
    public CancellationReviewRepository(TransactionManager transactions){this.transactions=transactions;}
    public String queue(UserPrincipal actor,int page,int size) {
        try{return transactions.execute(DatabasePrincipal.ADMIN,em->em.unwrap(Session.class).doReturningWork(connection->{
            try(var call=connection.prepareStatement("EXEC dbo.sp_event_cancellation_queue ?,?,?,?")) {
                call.setQueryTimeout(20);call.setString(1,actor.actorId().toString());call.setInt(2,actor.authVersion());call.setInt(3,page);call.setInt(4,size);
                try(var result=call.executeQuery()){if(!result.next())throw new SQLException("Missing queue response");return result.getString(1);}
            }
        }));}catch(RuntimeException failure){throw translated(failure);}
    }
    @Override public String execute(UserPrincipal actor,UUID event,String action,String reason,UUID key) {
        try {
            return transactions.execute(actor.isAdmin()?DatabasePrincipal.ADMIN:DatabasePrincipal.MANAGER,
                em->em.unwrap(Session.class).doReturningWork(connection->{
                    try(var call=connection.prepareStatement("EXEC dbo.sp_event_cancellation_review ?,?,?,?,?,?")) {
                        call.setQueryTimeout(20);call.setString(1,event.toString());call.setString(2,actor.actorId().toString());
                        call.setInt(3,actor.authVersion());call.setString(4,action);call.setString(5,reason);
                        call.setString(6,key==null?null:key.toString());
                        String json=null;boolean rows=call.execute();
                        // Drain all results before commit; a late SQL error must roll the operation back.
                        while(rows || call.getUpdateCount()!=-1) {
                            if(rows)try(var result=call.getResultSet()) {
                                while(result.next())if(json==null)json=result.getString("response_json");
                            }
                            rows=call.getMoreResults();
                        }
                        if(json==null)throw new SQLException("Missing cancellation review response");
                        return json;
                    }
                }));
        } catch(RuntimeException failure) {throw translated(failure);}
    }
    private static RefundException translated(RuntimeException failure) {
            for(Throwable cause=failure;cause!=null;cause=cause.getCause())if(cause instanceof SQLException sql) {
                int status=switch(sql.getErrorCode()){case 54300,54200->400;case 54301,54201->401;case 54303,54203->403;case 54304,54204->404;case 54309,54209->409;default->503;};
                String code=switch(status){case 400->"CANCELLATION_INVALID";case 401->"SESSION_EXPIRED";case 403->"CANCELLATION_FORBIDDEN";case 404->"EVENT_NOT_FOUND";case 409->"CANCELLATION_CONFLICT";default->"CANCELLATION_DATABASE_UNAVAILABLE";};
                throw new RefundException(status,code,"Không thể xử lý yêu cầu hủy. Kiểm tra quyền, trạng thái và thời gian sự kiện.");
            }
            throw new RefundException(503,"CANCELLATION_DATABASE_UNAVAILABLE","Hệ thống hủy chưa sẵn sàng.");
    }
}
