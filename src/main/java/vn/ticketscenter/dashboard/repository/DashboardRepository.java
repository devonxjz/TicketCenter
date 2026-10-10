package vn.ticketscenter.dashboard.repository;
import java.sql.*;
import java.time.*;
import java.util.*;
import vn.ticketscenter.report.repository.ReadProjection;

public final class DashboardRepository {
    public Map<String,Object> overview(Connection db,UUID org,Instant now)throws SQLException{
        Instant from=now.atOffset(ZoneOffset.UTC).toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC),to=from.plus(Duration.ofDays(1));
        var result=new LinkedHashMap<String,Object>();result.put("asOf",now);result.put("timezone","UTC");result.put("from",from);result.put("to",to);result.put("organizationId",org);
        if(org==null){result.put("totalUsers",count(db,"SELECT COUNT_BIG(*) FROM dbo.tc_users",List.of()));result.put("activeOrganizations",count(db,"SELECT COUNT_BIG(*) FROM dbo.tc_organizations WHERE status='APPROVED'",List.of()));result.put("pendingOrganizations",count(db,"SELECT COUNT_BIG(*) FROM dbo.tc_organizations WHERE status='PENDING_APPROVAL'",List.of()));result.put("pendingEvents",count(db,"SELECT COUNT_BIG(*) FROM dbo.tc_events WHERE status='PENDING_APPROVAL'",List.of()));result.put("pendingRefunds",count(db,"SELECT COUNT_BIG(*) FROM dbo.vw_VuongRefunds WHERE status='REQUESTED'",List.of()));result.put("draftSettlements",count(db,"SELECT COUNT_BIG(*) FROM dbo.tc_settlements WHERE status='DRAFT'",List.of()));}
        else {result.put("eventCount",count(db,"SELECT COUNT_BIG(*) FROM dbo.tc_events WHERE organization_id=?",List.of(org)));result.put("pendingEvents",count(db,"SELECT COUNT_BIG(*) FROM dbo.tc_events WHERE organization_id=? AND status='PENDING_APPROVAL'",List.of(org)));result.put("paidOrders",count(db,"SELECT COUNT_BIG(*) FROM dbo.vw_VuongOrders o JOIN dbo.tc_events e ON e.id=o.event_id WHERE e.organization_id=? AND o.status='PAID'",List.of(org)));}
        var cash=cash(db,org,from,to);var cohort=cohort(db,org,from,to);
        result.put("todayCashFlow",cash);result.put("todayCohortGrossRevenue",cohort.get("grossRevenue"));result.put("todayCohortCommission",cohort.get("totalCommission"));
        result.put("commissionTimeBasis","ORDER_PAID_AT_WITH_REFUNDS_BEFORE_TO");
        var days=new ArrayList<Map<String,Object>>();
        for(int previous=6;previous>=0;previous--){
            Instant dayFrom=from.minus(Duration.ofDays(previous)),dayTo=dayFrom.plus(Duration.ofDays(1));
            var dayCash=previous==0?cash:cash(db,org,dayFrom,dayTo);
            var dayCohort=previous==0?cohort:cohort(db,org,dayFrom,dayTo);
            var day=new LinkedHashMap<String,Object>();day.put("date",dayFrom.atOffset(ZoneOffset.UTC).toLocalDate().toString());
            day.put("from",dayFrom);day.put("to",dayTo);day.put("cohortGrossRevenue",dayCohort.get("grossRevenue"));day.put("netCashFlow",dayCash.get("netCashFlow"));days.add(day);
        }
        result.put("dailyMetrics",days);result.put("dailyMetricTimeBasis",Map.of("cohortGrossRevenue","ORDER_PAID_AT","netCashFlow","PAYMENT_PAID_AT_AND_REFUND_PROCESSED_AT"));
        return result;
    }
    private Map<String,Object> cash(Connection db,UUID org,Instant from,Instant to)throws SQLException{
        String sql="SELECT COALESCE(SUM(ticket_captured),0) ticket_captured,COALESCE(SUM(compensation_captured),0) compensation_captured,COALESCE(SUM(ticket_refund),0) ticket_refund,COALESCE(SUM(compensation_refund),0) compensation_refund,COALESCE(SUM(net_cash_flow),0) net_cash_flow FROM dbo.fn_GetOrganizationCashFlow(?,?,?)";
        try(var query=db.prepareStatement(sql)){ReadProjection.bind(query,parameters(org,from,to));try(var row=query.executeQuery()){row.next();return ReadProjection.row(row,List.of("ticket_captured","compensation_captured","ticket_refund","compensation_refund","net_cash_flow"));}}
    }
    private Map<String,Object> cohort(Connection db,UUID org,Instant from,Instant to)throws SQLException{
        String sql="SELECT COALESCE(SUM(gross_revenue),0) gross_revenue,COALESCE(SUM(total_commission),0) total_commission FROM dbo.fn_GetOrganizationRevenue(?,?,?)";
        try(var query=db.prepareStatement(sql)){ReadProjection.bind(query,parameters(org,from,to));try(var row=query.executeQuery()){row.next();return ReadProjection.row(row,List.of("gross_revenue","total_commission"));}}
    }
    private List<Object> parameters(UUID org,Instant from,Instant to){var result=new ArrayList<Object>();result.add(org);result.add(from);result.add(to);return result;}
    private long count(Connection db,String sql,List<Object> values)throws SQLException{try(var query=db.prepareStatement(sql)){ReadProjection.bind(query,values);try(var row=query.executeQuery()){row.next();return row.getLong(1);}}}
}
