package vn.ticketscenter.fulfillment.checkin.integration;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.config.DatabaseConfig;
import java.sql.*;
import java.time.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit, read-only acceptance against a provisioned SQL Server integration fixture; never skipped. */
class CheckInEventSqlServerIT {
    @Test void canonicalFunctionUsesUuidAndHalfOpenSixtyMinuteWindow() throws Exception {
        String database=required("TC_SQL_TEST_DB");
        String user=required("TC_SQL_TEST_LOGIN"),password=required("TC_SQL_TEST_PASSWORD");
        UUID event=UUID.fromString(required("TC_CHECKIN_TEST_EVENT_ID"));
        assertTrue(database.matches("[A-Za-z0-9_]+"),"Test database name must be a simple identifier");
        String url="jdbc:sqlserver://"+DatabaseConfig.getHost()+":"+DatabaseConfig.getPort()
                +";databaseName="+database+";encrypt=true;trustServerCertificate="+DatabaseConfig.isTrustServerCertificate()+";loginTimeout=5";
        try(Connection connection=DriverManager.getConnection(url,user,password)){
            try(PreparedStatement metadata=connection.prepareStatement("SELECT TYPE_NAME(user_type_id) FROM sys.parameters WHERE object_id=OBJECT_ID('dbo.fn_event_is_check_in_open') AND parameter_id=1");ResultSet rows=metadata.executeQuery()){
                assertTrue(rows.next(),"Canonical function is missing");assertEquals("uniqueidentifier",rows.getString(1),"Legacy BIGINT function cannot serve UUID endpoints");
            }
            Instant start,end;
            try(PreparedStatement query=connection.prepareStatement("SELECT start_time,end_time,status FROM dbo.tc_events WHERE id=?")){
                query.setString(1,event.toString());query.setQueryTimeout(5);
                try(ResultSet row=query.executeQuery()){
                    assertTrue(row.next(),"Provision a dedicated check-in test Event");assertEquals("PUBLISHED",row.getString(3));
                    start=instant(row,1);end=instant(row,2);
                }
            }
            try(PreparedStatement query=connection.prepareStatement("SELECT dbo.fn_event_is_check_in_open(?,?)")){
                query.setString(1,event.toString());query.setQueryTimeout(5);
                for(Instant time:new Instant[]{start.minusSeconds(3660),start.minusSeconds(3600),end.minusMillis(1),end}){
                    query.setObject(2,LocalDateTime.ofInstant(time,ZoneOffset.UTC));
                    try(ResultSet row=query.executeQuery()){assertTrue(row.next());assertEquals(!time.isBefore(start.minusSeconds(3600))&&time.isBefore(end),row.getBoolean(1),"Boundary: "+time);}
                }
            }
        }
    }
    private static Instant instant(ResultSet row,int column)throws SQLException {
        return row.getMetaData().getColumnType(column)==-155 ? row.getObject(column,OffsetDateTime.class).toInstant()
                : row.getObject(column,LocalDateTime.class).toInstant(ZoneOffset.UTC);
    }
    private static String required(String key){
        String value=AppConfig.get(key);assertNotNull(value,"Required dedicated integration setting: "+key);return value;
    }
}
