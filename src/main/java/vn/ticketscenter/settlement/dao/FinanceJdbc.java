package vn.ticketscenter.settlement.dao;

import java.sql.*;
import java.util.*;
import vn.ticketscenter.settlement.infrastructure.FinanceException;

public final class FinanceJdbc {
    private FinanceJdbc() {}
    private static final Set<String> PUBLIC_CODES=Set.of("ADMIN_REQUIRED","UNAUTHENTICATED","IDEMPOTENCY_CONFLICT","RULE_ALREADY_ATTACHED_TO_EVENTS","SETTLEMENT_BLOCKED","SETTLEMENT_NOT_DRAFT","PAYOUT_TERMS_CONFLICT","PAYOUT_RESULT_CONFLICT","PAYOUT_EXCEEDS_BALANCE","PAYOUT_RECONCILIATION_REQUIRED","SETTLEMENT_NOT_CONFIRMED","EVENT_NOT_ENDED","COMMISSION_RULE_INVALID","PAYOUT_INVALID","NOT_FOUND");
    public static FinanceException error(SQLException failure) {
        for(var current=failure;current!=null;current=current.getNextException()) {
            int n=current.getErrorCode();
            if(n==52701) return new FinanceException("UNAUTHENTICATED".equals(current.getMessage())?"UNAUTHENTICATED":"ADMIN_REQUIRED","UNAUTHENTICATED".equals(current.getMessage())?401:403);
            if(n==52704) return new FinanceException("NOT_FOUND",404);
            if(n==52702 || n==52709) return new FinanceException(PUBLIC_CODES.contains(current.getMessage())?current.getMessage():(n==52709?"FINANCE_CONFLICT":"FINANCE_INVALID"),n==52709?409:400);
        }
        return new FinanceException("DATABASE_UNAVAILABLE",503);
    }
    public static void bind(PreparedStatement query,Object... values) throws SQLException {
        query.setQueryTimeout(15);
        for(int i=0;i<values.length;i++) {
            Object value=values[i];
            if(value instanceof UUID id) query.setString(i+1,id.toString());
            else if(value instanceof java.time.Instant instant) query.setTimestamp(i+1,Timestamp.from(instant),utc());
            else query.setObject(i+1,value);
        }
    }
    public static Map<String,Object> row(ResultSet row) throws SQLException {
        var result=new LinkedHashMap<String,Object>(); var metadata=row.getMetaData();
        for(int i=1;i<=metadata.getColumnCount();i++) {
            Object value;
            int type=metadata.getColumnType(i);
            if(type==Types.TIMESTAMP || type==Types.TIMESTAMP_WITH_TIMEZONE) { var at=row.getTimestamp(i,utc()); value=at==null?null:at.toInstant(); }
            else value=row.getObject(i);
            String column=metadata.getColumnLabel(i);
            if((column.equals("id") || column.endsWith("_id")) && value instanceof String id
                    && id.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"))value=UUID.fromString(id).toString();
            result.put(camel(column),value);
        }
        return result;
    }
    public static List<Map<String,Object>> list(PreparedStatement query) throws SQLException {
        var results=new ArrayList<Map<String,Object>>();
        try(var rows=query.executeQuery()) { while(rows.next()) results.add(row(rows)); }
        return results;
    }
    public static Map<String,Object> call(Connection connection,String sql,Object... values) throws SQLException {
        try(var query=connection.prepareCall(sql)) {
            bind(query,values); boolean results=query.execute();
            while(!results && query.getUpdateCount()!=-1) results=query.getMoreResults();
            if(!results) throw new SQLException("Missing finance result");
            Map<String,Object> result;
            try(var rows=query.getResultSet()) {
                if(!rows.next()) throw new SQLException("Missing finance result");
                result=row(rows);
                if(rows.next()) throw new SQLException("Unexpected multiple finance results");
            }
            // Consume through COMMIT, including any late SQL error after the result set.
            while(query.getMoreResults() || query.getUpdateCount()!=-1) {
                try(var extra=query.getResultSet()) { if(extra!=null) while(extra.next()) { /* drain */ } }
            }
            return result;
        }
    }
    public static String camel(String column) {
        var out=new StringBuilder(); boolean upper=false;
        for(char c:column.toCharArray()) { if(c=='_') upper=true; else { out.append(upper?Character.toUpperCase(c):c); upper=false; } }
        return out.toString();
    }
    private static Calendar utc() { return Calendar.getInstance(TimeZone.getTimeZone("UTC")); }
}
