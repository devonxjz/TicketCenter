package vn.ticketscenter.database;

import java.nio.file.*;
import vn.ticketscenter.fulfillment.checkin.integration.ScanFixture;

/** Explicit disposable loopback target. Existing mode installs only the new additive contract. */
public final class EventCancellationCiSetup {
    public static void main(String[] args)throws Exception {
        boolean existing=args.length==1&&args[0].equals("--existing");
        boolean canonical=args.length==1&&args[0].equals("--canonical");
        boolean localCanonical=args.length==1&&args[0].equals("--local-canonical");
        if(localCanonical){installEmptyLocalCanonical();return;}
        if(args.length!=0&&!existing&&!canonical)throw new IllegalArgumentException("Unsupported fixture setup mode");
        if(!existing)RefundWorkerCiSetup.main(new String[0]);
        try(var c=ScanFixture.owner();var q=c.createStatement()) {
            c.setAutoCommit(false);
            try {
                q.execute("SET QUOTED_IDENTIFIER ON;SET ANSI_NULLS ON");
                if(canonical)for(String name:new String[]{"049_outbox_email_runtime.sql","050_consolidate_canonical_tables.sql"})
                    install(q,name);
                install(q,"058_khanh06_event_cancellation.sql");
                c.commit();
            }catch(Exception e){c.rollback();throw e;}
        }
    }
    private static void installEmptyLocalCanonical()throws Exception {
        String url=ScanFixture.required("TC_SCAN_TEST_URL");
        if(!url.startsWith("jdbc:sqlserver://127.0.0.1:")||!url.contains("databaseName=TC_Khanh06_Test_Canonical;")
                ||!"true".equals(System.getenv("TC_CANCELLATION_CANONICAL_TEST")))throw new IllegalStateException("New canonical loopback fixture required");
        try(var c=ScanFixture.owner();var q=c.createStatement()) {
            try(var r=q.executeQuery("SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped=0")) {
                r.next();if(r.getInt(1)!=0)throw new IllegalStateException("Canonical fixture must be empty; refusing overwrite");
            }
            q.execute("SET QUOTED_IDENTIFIER ON;SET ANSI_NULLS ON");
            var pending=new java.util.ArrayList<String>();
            try(var files=Files.list(Path.of("database/migrations"))) {
                for(var file:files.filter(p->p.getFileName().toString().matches("00.*\\.sql")).sorted().toList())
                    for(String batch:Files.readString(file).split("(?im)^\\s*GO\\s*\\r?$",-1))if(!batch.isBlank())pending.add(batch);
            }
            for(int pass=0;!pending.isEmpty()&&pass<8;pass++) {
                var next=new java.util.ArrayList<String>();for(String batch:pending)try{q.execute(batch);}catch(java.sql.SQLException e){if(!java.util.Set.of(208,4121,15151).contains(e.getErrorCode()))throw e;next.add(batch);}
                if(next.size()==pending.size())throw new IllegalStateException("Unresolved schema dependencies");pending=next;
            }
            if(!pending.isEmpty())throw new IllegalStateException("Incomplete canonical fixture");
            for(String name:new String[]{"030_khanh02_signed_checkin.sql","029_hold_runtime.sql","029_khanh03_refund_request.sql","030_order_runtime.sql"})install(q,name);
            try(var files=Files.list(Path.of("database/migrations"))) {
                for(var file:files.filter(p->p.getFileName().toString().matches("03[1-9]_sql_report_.*\\.sql")).sorted().toList())install(q,file.getFileName().toString());
            }
            for(String role:new String[]{"manager","auth","worker","buyer","admin"})q.execute("CREATE USER sql_report_"+role+" WITHOUT LOGIN;ALTER ROLE "+(role.equals("admin")?"tc_platform_admin":"tc_"+role)+" ADD MEMBER sql_report_"+role);
            for(String name:new String[]{"040_payment_ticket_runtime_reads.sql","041_hold_refund_runtime_compatibility.sql","042_checkin_event_user_lock_order.sql","043_coupon_admin.sql","044_buyer_order_history_views.sql","045_order_ticket_access_assertions.sql","043_khanh04_admin_refund.sql","045_event_commands_and_public_seats.sql","046_admin_refund_current_authority.sql","047_event_authority_lock_order.sql","048_khanh05_refund_worker.sql","049_outbox_email_runtime.sql","050_consolidate_canonical_tables.sql","058_khanh06_event_cancellation.sql"})install(q,name);
            System.out.println("Fresh canonical-050 cancellation fixture installed; no existing database was changed.");
        }
    }
    private static void install(java.sql.Statement q,String name)throws Exception {
        for(String batch:Files.readString(Path.of("database/migrations",name)).split("(?im)^\\s*GO\\s*\\r?$",-1))
            if(!batch.isBlank())q.execute(batch);
    }
}
