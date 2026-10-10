package vn.ticketscenter.report.repository;

import java.sql.*;
import java.util.*;
import vn.ticketscenter.report.dto.ReportFilter;
import vn.ticketscenter.settlement.infrastructure.FinanceException;

public final class ReportRepository {
    private static final Set<String> MONEY=Set.of("gross_revenue","total_refund","remaining_amount","total_commission","net_payable","ticket_captured","compensation_captured","ticket_refund","compensation_refund","net_cash_flow","paid_amount","pending_amount","available_to_pay");
    public void requireEventScope(Connection db,ReportFilter filter)throws SQLException{
        if(filter.eventId()==null)return;
        String sql="SELECT id FROM dbo.tc_events WHERE id=?"+(filter.organizationId()==null?"":" AND organization_id=?");
        try(var query=db.prepareStatement(sql)){query.setString(1,filter.eventId().toString());if(filter.organizationId()!=null)query.setString(2,filter.organizationId().toString());try(var row=query.executeQuery()){if(!row.next())throw new FinanceException("NOT_FOUND",404);}}
    }
    public Map<String,Object> page(Connection db,ReportFilter filter)throws SQLException{
        Query q=query(filter);long total;
        try(var count=db.prepareStatement("SELECT COUNT_BIG(*) FROM ("+q.sql+") report_rows")){ReadProjection.bind(count,q.parameters);try(var row=count.executeQuery()){row.next();total=row.getLong(1);}}
        var rows=new ArrayList<Map<String,Object>>();
        try(var statement=db.prepareStatement(q.sql+" ORDER BY "+q.order+" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY")){
            var params=new ArrayList<>(q.parameters);params.add(filter.offset());params.add(filter.pageSize());ReadProjection.bind(statement,params);
            try(var data=statement.executeQuery()){while(data.next())rows.add(ReadProjection.row(data,q.columns));}
        }
        var summary=new LinkedHashMap<String,Object>();summary.put("totalEvents",total);
        var totals=q.columns.stream().filter(c->c.endsWith("_count")||MONEY.contains(c)).toList();
        if(!totals.isEmpty())try(var statement=db.prepareStatement("SELECT "+String.join(",",totals.stream().map(c->"COALESCE(SUM("+c+"),0) "+c).toList())+" FROM ("+q.sql+") report_rows")){
            ReadProjection.bind(statement,q.parameters);try(var result=statement.executeQuery()){result.next();summary.putAll(ReadProjection.row(result,totals));}
        }
        var top=new ArrayList<Map<String,Object>>();
        if(q.columns.contains("gross_revenue"))try(var statement=db.prepareStatement("SELECT TOP(5) * FROM ("+q.sql+") report_rows ORDER BY gross_revenue DESC,"+q.order)){
            ReadProjection.bind(statement,q.parameters);try(var result=statement.executeQuery()){while(result.next())top.add(ReadProjection.row(result,q.columns));}
        }
        var data=new LinkedHashMap<String,Object>();data.put("metric",filter.metric().name());data.put("timeBasis",timeBasis(filter));data.put("timezone","UTC");data.put("from",filter.from());data.put("to",filter.to());data.put("organizationId",filter.organizationId());data.put("eventId",filter.eventId());data.put("summary",summary);data.put("topEvents",top);data.put("rows",rows);data.put("page",filter.page());data.put("pageSize",filter.pageSize());data.put("total",total);return data;
    }
    public Cursor cursor(Connection db,ReportFilter filter)throws SQLException{
        Query q=query(filter);var statement=db.prepareStatement(q.sql+" ORDER BY "+q.order,ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY);
        try{statement.setFetchSize(64);ReadProjection.bind(statement,q.parameters);return new Cursor(statement,statement.executeQuery(),q.columns);}catch(SQLException|RuntimeException e){statement.close();throw e;}
    }
    public static String timeBasis(ReportFilter f){return switch(f.metric()){case COHORT_REVENUE->"ORDER_PAID_AT_WITH_REFUNDS_BEFORE_TO";case CASH_FLOW->"PAYMENT_PAID_AT_AND_REFUND_PROCESSED_AT";case EVENT_SALES,SETTLEMENT_BALANCE->"EVENT_END_TIME_CURRENT_OR_CONFIRMED_TOTALS";};}
    private Query query(ReportFilter f){
        List<String> columns;String source,id="event_id",date="";var parameters=new ArrayList<Object>();
        switch(f.metric()){
            case COHORT_REVENUE->{columns=List.of("event_id","organization_id","paid_order_count","gross_revenue","total_refund","remaining_amount","total_commission","net_payable");source="dbo.fn_GetOrganizationRevenue(?,?,?)";parameters.add(f.organizationId());parameters.add(f.from());parameters.add(f.to());}
            case CASH_FLOW->{columns=List.of("event_id","organization_id","ticket_captured","compensation_captured","ticket_refund","compensation_refund","net_cash_flow");source="dbo.fn_GetOrganizationCashFlow(?,?,?)";parameters.add(f.organizationId());parameters.add(f.from());parameters.add(f.to());}
            case EVENT_SALES->{columns=List.of("event_id","organization_id","event_title","event_status","end_time","paid_order_count","ticket_count","valid_ticket_count","refunded_ticket_count","tickets_checked_in_count","gross_revenue","total_refund","total_commission","net_payable","settlement_id","settlement_status","is_confirmed");source="(SELECT v.*,(SELECT COUNT_BIG(*) FROM dbo.tc_tickets t JOIN dbo.tc_order_items i ON i.id=t.order_item_id JOIN dbo.vw_VuongOrders o ON o.id=i.order_id WHERE o.event_id=v.event_id AND t.used_at IS NOT NULL) tickets_checked_in_count FROM dbo.vw_EventSalesReport v) event_sales";date=" AND end_time>=? AND end_time<?";}
            case SETTLEMENT_BALANCE->{columns=List.of("settlement_id","event_id","organization_id","status","gross_revenue","total_refund","total_commission","net_payable","paid_amount","pending_amount","available_to_pay","confirmed_at");source="dbo.vw_SettlementPayoutBalance";id="settlement_id";date=" AND EXISTS (SELECT 1 FROM dbo.tc_events e WHERE e.id=event_id AND e.end_time>=? AND e.end_time<?)";}
            default->throw new FinanceException("INVALID_REPORT_FILTER",400);
        }
        String sql="SELECT "+String.join(",",columns)+" FROM "+source+" WHERE 1=1";
        if(f.organizationId()!=null){sql+=" AND organization_id=?";parameters.add(f.organizationId());}
        if(f.eventId()!=null){sql+=" AND event_id=?";parameters.add(f.eventId());}
        if(!date.isEmpty()){sql+=date;parameters.add(f.from());parameters.add(f.to());}
        return new Query(sql,parameters,columns,id);
    }
    private record Query(String sql,List<Object> parameters,List<String> columns,String order){}
    public static final class Cursor implements AutoCloseable,Iterable<List<String>>{
        private final PreparedStatement statement;private final ResultSet rows;private final List<String> columns;private boolean iterated;
        Cursor(PreparedStatement statement,ResultSet rows,List<String> columns){this.statement=statement;this.rows=rows;this.columns=columns;}
        public List<String> headers(){return columns.stream().map(ReadProjection::camel).toList();}
        @Override public Iterator<List<String>> iterator(){if(iterated)throw new IllegalStateException("Cursor can be consumed once");iterated=true;return new Iterator<>(){boolean ready,has;
            public boolean hasNext(){if(!ready){try{has=rows.next();ready=true;}catch(SQLException e){throw new FinanceException("REPORT_UNAVAILABLE",503);}}return has;}
            public List<String> next(){if(!hasNext())throw new NoSuchElementException();ready=false;try{var row=ReadProjection.row(rows,columns);var result=new ArrayList<String>();for(Object value:row.values())result.add(value==null?"":value instanceof java.math.BigDecimal money?money.toPlainString():value.toString());return result;}catch(SQLException e){throw new FinanceException("REPORT_UNAVAILABLE",503);}}
        };}
        @Override public void close()throws SQLException{try{rows.close();}finally{statement.close();}}
    }
}
