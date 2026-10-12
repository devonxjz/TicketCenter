package vn.ticketscenter.database;

import vn.ticketscenter.fulfillment.checkin.integration.ScanCiSetup;
import vn.ticketscenter.fulfillment.checkin.integration.ScanFixture;
import java.nio.file.*;

/** CI service database only; no externally configurable target. */
public final class SqlReportCiSetup {
    public static void main(String[] args) throws Exception {
        ScanCiSetup.main(args);
        try(var c=ScanFixture.owner();var q=c.createStatement()){
            c.setAutoCommit(false);
            try {
                q.execute("SET QUOTED_IDENTIFIER ON; SET ANSI_NULLS ON;");
                for(String name:new String[]{"029_hold_runtime.sql","029_khanh03_refund_request.sql","030_order_runtime.sql"})
                    for(String batch:Files.readString(Path.of("database/migrations",name)).split("(?im)^\\s*GO\\s*\\r?$",-1))if(!batch.isBlank())q.execute(batch);
                try(var files=Files.list(Path.of("database/migrations"))){
                    for(var file:files.filter(p->p.getFileName().toString().matches("03[1-9]_sql_report_.*\\.sql")).sorted().toList())
                        for(String batch:Files.readString(file).split("(?im)^\\s*GO\\s*\\r?$",-1))if(!batch.isBlank())q.execute(batch);
                }
                for(String role:new String[]{"manager","auth","worker","buyer","admin"}){
                    String dbRole=role.equals("admin")?"tc_platform_admin":"tc_"+role;
                    q.execute("CREATE USER sql_report_"+role+" WITHOUT LOGIN; ALTER ROLE "+dbRole+" ADD MEMBER sql_report_"+role+";");
                }
                // Ledger identities are complete basenames; preserve installed refund043.
                for(String name:new String[]{"040_payment_ticket_runtime_reads.sql","041_hold_refund_runtime_compatibility.sql","042_checkin_event_user_lock_order.sql","043_coupon_admin.sql","044_buyer_order_history_views.sql","043_khanh04_admin_refund.sql","045_event_commands_and_public_seats.sql","046_admin_refund_current_authority.sql","047_event_authority_lock_order.sql"})
                    for(String batch:Files.readString(Path.of("database/migrations",name)).split("(?im)^\\s*GO\\s*\\r?$",-1))if(!batch.isBlank())q.execute(batch);
                c.commit();
            }catch(Exception ex){c.rollback();throw ex;}
        }
    }
}
