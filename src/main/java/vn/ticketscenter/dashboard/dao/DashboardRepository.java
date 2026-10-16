package vn.ticketscenter.dashboard.dao;
import java.sql.*;
import java.time.*;
import java.util.*;
import vn.ticketscenter.report.dao.ReadProjection;

public final class DashboardRepository {
    public Map<String,Object> overview(Connection db,UUID org,Instant now)throws SQLException{
        Instant from=now.atOffset(ZoneOffset.UTC).toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC),to=from.plus(Duration.ofDays(1));
        var result=new LinkedHashMap<String,Object>();result.put("asOf",now);result.put("timezone","UTC");result.put("from",from);result.put("to",to);result.put("organizationId",org);
        if(org==null){var counts=counts(db,"SELECT total_users,active_organizations,pending_organizations,pending_events,pending_refunds,draft_settlements FROM dbo.vw_DashboardPlatformCounts",List.of(),6);
            result.put("totalUsers",counts[0]);result.put("activeOrganizations",counts[1]);result.put("pendingOrganizations",counts[2]);result.put("pendingEvents",counts[3]);result.put("pendingRefunds",counts[4]);result.put("draftSettlements",counts[5]);}
        else {var counts=counts(db,"SELECT event_count,pending_events,paid_orders FROM dbo.fn_DashboardOrganizationCounts(?)",List.of(org),3);
            result.put("eventCount",counts[0]);result.put("pendingEvents",counts[1]);result.put("paidOrders",counts[2]);}
        var cash=cash(db,org,from,to);var cohort=cohort(db,org,from,to);
        result.put("todayCashFlow",cash);result.put("todayCohortGrossRevenue",cohort.get("grossRevenue"));result.put("todayCohortCommission",cohort.get("totalCommission"));
        var cumulative=cohort(db,org,Instant.EPOCH,now);
        result.put("cumulativeGrossRevenue",cumulative.get("grossRevenue"));
        result.put("cumulativeCommission",cumulative.get("totalCommission"));
        result.put("cumulativeNetCashFlow",cash(db,org,Instant.EPOCH,now).get("netCashFlow"));
        result.put("cumulativeFrom",Instant.EPOCH);result.put("cumulativeTo",now);
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
    private long[] counts(Connection db,String sql,List<Object> values,int columns)throws SQLException{try(var query=db.prepareStatement(sql)){ReadProjection.bind(query,values);try(var row=query.executeQuery()){row.next();long[] result=new long[columns];for(int index=0;index<columns;index++)result[index]=row.getLong(index+1);return result;}}}
}
