package vn.ticketscenter.audit.dto;
import java.util.*;
import java.time.Instant;
import vn.ticketscenter.report.dto.ReportFilter;
import vn.ticketscenter.settlement.infrastructure.FinanceException;
public record AuditFilter(Instant from,Instant to,String action,String entityType,String entityId,UUID organizationId,UUID eventId,int page,int pageSize){
    public static AuditFilter parse(Map<String,String> values){try{
        var shared=ReportFilter.parse(values,null);String action=text(values.get("action"),"[A-Z][A-Z0-9_]{0,99}"),type=text(values.get("entityType"),"[A-Za-z][A-Za-z0-9_]{0,99}"),id=text(values.get("entityId"),"[A-Za-z0-9_-]{1,100}");
        return new AuditFilter(shared.from(),shared.to(),action,type,id,shared.organizationId(),shared.eventId(),shared.page(),shared.pageSize());
    }catch(RuntimeException e){throw new FinanceException("INVALID_AUDIT_FILTER",400);}}
    private static String text(String value,String pattern){if(value==null || value.isBlank())return null;if(!value.matches(pattern))throw new IllegalArgumentException();return value;}
    public int offset(){return(page-1)*pageSize;}
}
