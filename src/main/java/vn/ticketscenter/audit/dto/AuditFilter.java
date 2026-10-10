package vn.ticketscenter.audit.dto;
import java.util.*;
import java.time.Instant;
import vn.ticketscenter.report.dto.ReportFilter;
import vn.ticketscenter.settlement.infrastructure.FinanceException;
public record AuditFilter(Instant from,Instant to,String action,String entityType,String entityId,UUID organizationId,UUID eventId,UUID actorId,int page,int pageSize){
    private static final Map<String,String> TARGET_TYPES=Map.ofEntries(
        Map.entry("tc_users","User"),Map.entry("tc_organizations","Organization"),Map.entry("tc_organization_memberships","OrganizationMembership"),
        Map.entry("tc_events","Event"),Map.entry("tc_commission_rules","CommissionRule"),Map.entry("tc_settlements","Settlement"),Map.entry("tc_settlement_transfer_logs","SettlementTransferLog"),
        Map.entry("tc_orders","Order"),Map.entry("tc_order_items","OrderItem"),Map.entry("tc_payments","Payment"),Map.entry("tc_refunds","Refund"),Map.entry("tc_refund_transfer_logs","RefundTransferLog"),
        Map.entry("tc_tickets","Ticket"),Map.entry("tc_ticket_holds","TicketHold"));
    public static AuditFilter parse(Map<String,String> values){try{
        var shared=ReportFilter.parse(values,null);String action=text(values.get("action"),"[A-Z][A-Z0-9_]{0,99}"),type=text(values.get("entityType"),"[A-Za-z][A-Za-z0-9_]{0,99}"),id=text(values.get("entityId"),"[A-Za-z0-9_-]{1,100}");
        String target=values.get("targetTable");
        if(target!=null && !target.isBlank()){
            String alias=target.startsWith("dbo.")?target.substring(4):target;
            String mapped=TARGET_TYPES.get(alias);
            if(mapped==null || (type!=null && !type.equals(mapped)))throw new IllegalArgumentException();
            type=mapped;
        }
        return new AuditFilter(shared.from(),shared.to(),action,type,id,shared.organizationId(),shared.eventId(),ReportFilter.uuid(values.get("actorId")),shared.page(),shared.pageSize());
    }catch(RuntimeException e){throw new FinanceException("INVALID_AUDIT_FILTER",400);}}
    private static String text(String value,String pattern){if(value==null || value.isBlank())return null;if(!value.matches(pattern))throw new IllegalArgumentException();return value;}
    public int offset(){return(page-1)*pageSize;}
}
