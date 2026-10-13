package vn.ticketscenter.database;

import java.nio.file.*;
import vn.ticketscenter.fulfillment.checkin.integration.ScanFixture;

/** Explicit disposable loopback target. Installs cancellation and current WAR dependencies. */
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
                installCurrentRuntime(q);
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
            installCurrentRuntime(q);
            System.out.println("Fresh canonical-050 cancellation fixture installed; no existing database was changed.");
        }
    }
    private static void installCurrentRuntime(java.sql.Statement q)throws Exception {
        // The complete WAR must exercise current dev's read models and image/worker contracts.
        // This is fixture-only and deliberately leaves historical shared migration bytes unchanged.
        boolean legacyRefunds;
        try(var r=q.executeQuery("SELECT CASE WHEN OBJECT_ID('dbo.tc_refunds','U') IS NOT NULL THEN 1 ELSE 0 END")) {
            r.next();legacyRefunds=r.getInt(1)==1;
        }
        for(String batch:Files.readString(Path.of("database/migrations/027_team_schema_adapter.sql")).split("(?im)^\\s*GO\\s*\\r?$",-1)) {
            if(batch.contains("Finance adapter requires the installed owner schema"))q.execute(batch);
            int refund=batch.indexOf("DECLARE @refunds sysname");
            if(refund>=0&&legacyRefunds){q.execute(batch);continue;}
            if(refund>=0)q.execute(batch.substring(0,refund)); // Exact order/payment read projections.
            int audit=batch.indexOf("IF COL_LENGTH('dbo.tc_audit_logs','entity_type') IS NOT NULL");
            if(audit>=0)q.execute(batch.substring(audit));
            // Never reinstall this historical adapter's legacy-refund projection after 050:
            // 050 already provides the canonical projection without the deleted attempt table.
        }
        // Current read-model 057 depends on these committed finance metadata/projection
        // batches. Do not reinstall finance write procedures over later safety migrations.
        for(String batch:Files.readString(Path.of("database/migrations/027_vuong_finance.sql")).split("(?im)^\\s*GO\\s*\\r?$",-1))
            if(batch.contains("DECLARE @required TABLE")||batch.contains("CREATE OR ALTER VIEW dbo.vw_SettlementPayoutBalance AS"))q.execute(batch);
        for(String name:new String[]{"028_event_image_workflow.sql","051_event_single_image_url.sql","052_drop_unused_objects.sql",
                "053_outbox_worker_procedures.sql","054_organization_decision_procedures.sql","055_event_image_procedures.sql",
                "056_organization_submit_and_grant_cleanup.sql","057_application_read_models.sql","059_profile_update.sql",
                "060_compensation_retry_and_neutral_views.sql"}) {
            // The historical SSMS cleanup checks this table with a static reference. Fresh
            // service fixtures have never used SSMS; provide its empty input, then run the
            // exact immutable migration which removes it. Never change installed 052 bytes.
            if(name.equals("052_drop_unused_objects.sql"))q.execute("IF OBJECT_ID('dbo.sysdiagrams','U') IS NULL CREATE TABLE dbo.sysdiagrams(diagram_id int PRIMARY KEY)");
            install(q,name);
        }
    }
    private static void install(java.sql.Statement q,String name)throws Exception {
        for(String batch:Files.readString(Path.of("database/migrations",name)).split("(?im)^\\s*GO\\s*\\r?$",-1))
            if(!batch.isBlank())q.execute(batch);
    }
}
