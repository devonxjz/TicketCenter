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
                try(var files=Files.list(Path.of("database/migrations"))){
                    for(var file:files.filter(p->p.getFileName().toString().matches("03[1-9]_sql_report_.*\\.sql")).sorted().toList())
                        for(String batch:Files.readString(file).split("(?im)^\\s*GO\\s*\\r?$",-1))if(!batch.isBlank())q.execute(batch);
                }
                for(String role:new String[]{"manager","auth","worker","buyer","admin"}){
                    String dbRole=role.equals("admin")?"tc_platform_admin":"tc_"+role;
                    q.execute("CREATE USER sql_report_"+role+" WITHOUT LOGIN; ALTER ROLE "+dbRole+" ADD MEMBER sql_report_"+role+";");
                }
                c.commit();
            }catch(Exception ex){c.rollback();throw ex;}
        }
    }
}
