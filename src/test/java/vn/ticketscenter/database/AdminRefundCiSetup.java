package vn.ticketscenter.database;

import java.nio.file.*;
import vn.ticketscenter.fulfillment.checkin.integration.ScanFixture;

/** Dedicated loopback fixture only. --existing preserves prior fixture data and installs missing contracts. */
public final class AdminRefundCiSetup {
    public static void main(String[] args)throws Exception {
        boolean existing=args.length==1&&args[0].equals("--existing");
        if(!existing)SqlReportCiSetup.main(args);
        String adminPassword=System.getenv("TC_ADMIN_REFUND_DB_PASSWORD");
        if(adminPassword!=null){
            if(!adminPassword.matches("K04![a-fA-F0-9]{48}"))throw new IllegalArgumentException("Disposable admin credential required");
            try(var master=java.sql.DriverManager.getConnection("jdbc:sqlserver://127.0.0.1:1433;databaseName=master;encrypt=true;trustServerCertificate=true;loginTimeout=5","sa",ScanFixture.required("TC_SCAN_SA_PASSWORD"));var q=master.createStatement()){
                q.execute("CREATE LOGIN khanh04_admin WITH PASSWORD='"+adminPassword+"',CHECK_POLICY=ON;");
            }
        }
        try(var c=ScanFixture.owner();var q=c.createStatement()) {
            c.setAutoCommit(false);
            try {
                q.execute("SET QUOTED_IDENTIFIER ON; SET ANSI_NULLS ON;");
                if(existing) {
                    String[] files={"029_hold_runtime.sql","029_khanh03_refund_request.sql","030_order_runtime.sql"};
                    String[] markers={"usp_HoldSnapshot","v_refund_tickets","usp_OrderSnapshot"};
                    for(int i=0;i<files.length;i++)try(var r=q.executeQuery("SELECT OBJECT_ID('dbo."+markers[i]+"')")){r.next();if(r.getObject(1)==null)install(c,files[i]);}
                    for(String file:new String[]{"040_payment_ticket_runtime_reads.sql","041_hold_refund_runtime_compatibility.sql","042_checkin_event_user_lock_order.sql","043_coupon_admin.sql","044_buyer_order_history_views.sql"})install(c,file);
                    for(String role:new String[]{"admin","buyer","worker"})q.execute("IF USER_ID('sql_report_"+role+"') IS NULL BEGIN CREATE USER sql_report_"+role+" WITHOUT LOGIN; ALTER ROLE "+(role.equals("admin")?"tc_platform_admin":"tc_"+role)+" ADD MEMBER sql_report_"+role+"; END;");
                }
                install(c,"043_khanh04_admin_refund.sql");
                // Existing upstream Event contract is used only inside the guarded fixture.
                installFile(c,Path.of("docs/evidence/dong/DONG-DB-CONTRACT.sql"));
                if(adminPassword!=null)q.execute("CREATE USER khanh04_admin FOR LOGIN khanh04_admin; ALTER ROLE tc_platform_admin ADD MEMBER khanh04_admin; GRANT VIEW DEFINITION TO khanh04_admin;");
                c.commit();
            }catch(Exception failure){c.rollback();throw failure;}
        }
    }
    private static void install(java.sql.Connection c,String file)throws Exception {
        installFile(c,Path.of("database/migrations",file));
    }
    private static void installFile(java.sql.Connection c,Path file)throws Exception {
        try(var q=c.createStatement()){q.setQueryTimeout(30);for(String batch:Files.readString(file).split("(?im)^\\s*GO\\s*\\r?$",-1))if(!batch.isBlank())q.execute(batch);}
    }
}
