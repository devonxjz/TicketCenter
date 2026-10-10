package vn.ticketscenter.dashboard.repository;
import java.sql.*;
import java.time.*;
import java.util.*;
import vn.ticketscenter.report.repository.ReadProjection;

public final class DashboardRepository {
    public Map<String,Object> overview(Connection db,UUID org,Instant now)throws SQLException{
        Instant from=now.atOffset(ZoneOffset.UTC).toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC),to=from.plus(Duration.ofDays(1));
        var result=new LinkedHashMap<String,Object>();result.put("asOf",now);result.put("timezone","UTC");result.put("from",from);result.put("to",to);result.put("organizationId",org);
        if(org==null){result.put("pendingOrganizations",count(db,"SELECT COUNT_BIG(*) FROM dbo.tc_organizations WHERE status='PENDING_APPROVAL'",List.of()));result.put("pendingEvents",count(db,"SELECT COUNT_BIG(*) FROM dbo.tc_events WHERE status='PENDING_APPROVAL'",List.of()));result.put("pendingRefunds",count(db,"SELECT COUNT_BIG(*) FROM dbo.tc_refunds WHERE status='REQUESTED'",List.of()));result.put("draftSettlements",count(db,"SELECT COUNT_BIG(*) FROM dbo.tc_settlements WHERE status='DRAFT'",List.of()));}
        else {result.put("eventCount",count(db,"SELECT COUNT_BIG(*) FROM dbo.tc_events WHERE organization_id=?",List.of(org)));result.put("pendingEvents",count(db,"SELECT COUNT_BIG(*) FROM dbo.tc_events WHERE organization_id=? AND status='PENDING_APPROVAL'",List.of(org)));result.put("paidOrders",count(db,"SELECT COUNT_BIG(*) FROM dbo.tc_orders o JOIN dbo.tc_events e ON e.id=o.event_id WHERE e.organization_id=? AND o.status='PAID'",List.of(org)));}
        var parameters=new ArrayList<Object>();parameters.add(org);parameters.add(from);parameters.add(to);
        var money=new LinkedHashMap<String,Object>();String cash="SELECT COALESCE(SUM(ticket_captured),0) ticket_captured,COALESCE(SUM(compensation_captured),0) compensation_captured,COALESCE(SUM(ticket_refund),0) ticket_refund,COALESCE(SUM(compensation_refund),0) compensation_refund,COALESCE(SUM(net_cash_flow),0) net_cash_flow FROM dbo.fn_GetOrganizationCashFlow(?,?,?)";
        try(var query=db.prepareStatement(cash)){ReadProjection.bind(query,parameters);try(var row=query.executeQuery()){row.next();money.putAll(ReadProjection.row(row,List.of("ticket_captured","compensation_captured","ticket_refund","compensation_refund","net_cash_flow")));}}
        result.put("todayCashFlow",money);
        try(var query=db.prepareStatement("SELECT COALESCE(SUM(total_commission),0) total_commission FROM dbo.fn_GetOrganizationRevenue(?,?,?)")){ReadProjection.bind(query,parameters);try(var row=query.executeQuery()){row.next();result.put("todayCohortCommission",row.getBigDecimal(1));}}
        result.put("commissionTimeBasis","ORDER_PAID_AT_WITH_REFUNDS_BEFORE_TO");return result;
    }
    private long count(Connection db,String sql,List<Object> values)throws SQLException{try(var query=db.prepareStatement(sql)){ReadProjection.bind(query,values);try(var row=query.executeQuery()){row.next();return row.getLong(1);}}}
}
