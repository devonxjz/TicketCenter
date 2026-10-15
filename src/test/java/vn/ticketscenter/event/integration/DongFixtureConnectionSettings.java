package vn.ticketscenter.event.integration;

import java.sql.*;
import java.util.regex.Pattern;
import vn.ticketscenter.config.AppConfig;

/** Exact dedicated target allowlist. SQL credentials never become JDBC URL properties. */
record DongFixtureConnectionSettings(String url,String database,boolean integrated,String user,String password) {
    private static final Pattern TARGET=Pattern.compile("jdbc:sqlserver://(?:127\\.0\\.0\\.1|localhost):([0-9]{1,5});databaseName=(TicketCenter_Dong_[A-Za-z0-9_]+);(integratedSecurity=true;)?encrypt=true;trustServerCertificate=true;loginTimeout=5;?");
    static DongFixtureConnectionSettings configured() {
        if(!AppConfig.getBoolean("TC_DONG_ACCEPTANCE",false))throw new IllegalArgumentException("Explicit dedicated Event acceptance required");
        return parse(AppConfig.get("TC_DONG_SQL_URL"),AppConfig.get("TC_DONG_DB_USER"),AppConfig.get("TC_DONG_DB_PASSWORD"));
    }
    static DongFixtureConnectionSettings parse(String url,String user,String password) {
        if(url==null)throw new IllegalArgumentException("Dedicated Event connection required");
        var target=TARGET.matcher(url);
        if(!target.matches())throw new IllegalArgumentException("Only the exact dedicated loopback URL is allowed; no JDBC overrides");
        int port=Integer.parseInt(target.group(1));
        if(port<1||port>65535)throw new IllegalArgumentException("Invalid dedicated TCP port");
        boolean integrated=target.group(3)!=null;
        if(integrated?(user!=null||password!=null):(user==null||user.isBlank()||password==null||password.isBlank()))
            throw new IllegalArgumentException("Use one complete explicit authentication method");
        return new DongFixtureConnectionSettings(url,target.group(2),integrated,user,password);
    }
    Connection open() throws SQLException {
        Connection connection=integrated?DriverManager.getConnection(url):DriverManager.getConnection(url,user,password);
        try(var query=connection.createStatement()) {
            query.setQueryTimeout(5);
            try(var rows=query.executeQuery("SELECT DB_NAME()")) {
                if(!rows.next()||!database.equals(rows.getString(1)))throw new SQLException("Refusing unexpected Event fixture database");
            }
            return connection;
        }catch(SQLException|RuntimeException failure){connection.close();throw failure;}
    }
    @Override public String toString(){return "DongFixtureConnectionSettings[database="+database+", authentication="+(integrated?"WINDOWS":"SQL")+"]";}
}
