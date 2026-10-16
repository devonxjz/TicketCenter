package vn.ticketscenter.report.dao;

import java.sql.*;
import java.util.*;

/** Explicit scalar columns only; never serializes a JDBC/persistence object graph. */
public final class ReadProjection {
    private ReadProjection() {}
    public static Map<String,Object> row(ResultSet row,List<String> columns)throws SQLException{
        var result=new LinkedHashMap<String,Object>();
        for(String column:columns){Object value;
            if(column.endsWith("_at") || column.equals("end_time")){var timestamp=row.getTimestamp(column,Calendar.getInstance(TimeZone.getTimeZone("UTC")));value=timestamp==null?null:timestamp.toInstant();}
            else if(column.equals("is_confirmed")){value=row.getBoolean(column);}
            else if(column.endsWith("_count")){value=row.getLong(column);}
            else if(isMoney(column)){value=row.getBigDecimal(column);}
            else {value=row.getString(column);if(value instanceof String text && (column.equals("id") || column.endsWith("_id")) && text.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"))value=UUID.fromString(text).toString();}
            result.put(camel(column),value);
        }return result;
    }
    public static String camel(String value){var out=new StringBuilder();boolean upper=false;for(char c:value.toCharArray()){if(c=='_'){upper=true;}else{out.append(upper?Character.toUpperCase(c):c);upper=false;}}return out.toString();}
    private static boolean isMoney(String c){return Set.of("gross_revenue","total_refund","remaining_amount","total_commission","net_payable","ticket_captured","compensation_captured","ticket_refund","compensation_refund","net_cash_flow","paid_amount","pending_amount","available_to_pay").contains(c);}
    public static void bind(PreparedStatement statement,List<Object> parameters)throws SQLException{
        for(int i=0;i<parameters.size();i++){Object value=parameters.get(i);int n=i+1;if(value instanceof java.time.Instant instant)statement.setTimestamp(n,Timestamp.from(instant),Calendar.getInstance(TimeZone.getTimeZone("UTC")));else if(value instanceof UUID id)statement.setString(n,id.toString());else if(value==null)statement.setNull(n,Types.VARCHAR);else statement.setObject(n,value);}
    }
}
