package vn.ticketscenter.fulfillment.checkin.integration;
import java.sql.*;
import java.nio.file.*;
import java.util.*;
/** Schema setup exclusively for the CI service container on loopback. */
public final class ScanCiSetup {
    public static void main(String[] args)throws Exception{
        String secret=ScanFixture.required("TC_SCAN_OWNER_PASSWORD"),staff=ScanFixture.required("TC_SCAN_CHECKIN_PASSWORD");
        if(!secret.matches("K02![a-fA-F0-9]{40,64}")||!staff.matches("K02![a-fA-F0-9]{40,64}"))throw new IllegalStateException("Disposable credentials required");
        try(var c=DriverManager.getConnection("jdbc:sqlserver://127.0.0.1:1433;databaseName=master;encrypt=true;trustServerCertificate=true;loginTimeout=5","sa",ScanFixture.required("TC_SCAN_SA_PASSWORD"))){
            try(var q=c.createStatement()){
                q.execute("CREATE DATABASE TicketCenter_Khanh02_Test");
                q.execute("CREATE LOGIN khanh02_owner WITH PASSWORD='"+secret+"',CHECK_POLICY=ON;CREATE LOGIN khanh02_checkin WITH PASSWORD='"+staff+"',CHECK_POLICY=ON;");
                q.execute("USE TicketCenter_Khanh02_Test;CREATE USER khanh02_owner FOR LOGIN khanh02_owner;CREATE USER khanh02_checkin FOR LOGIN khanh02_checkin;ALTER ROLE db_owner ADD MEMBER khanh02_owner;SET QUOTED_IDENTIFIER ON;SET ANSI_NULLS ON;");
                List<String> pending=new ArrayList<>();try(var files=Files.list(Path.of("database/migrations"))){for(Path file:files.filter(p->p.getFileName().toString().matches("00.*\\.sql")).sorted().toList())pending.addAll(Arrays.stream(Files.readString(file).split("(?im)^\\s*GO\\s*\\r?$",-1)).filter(s->!s.isBlank()).toList());}
                for(int pass=0;!pending.isEmpty()&&pass<8;pass++){List<String> next=new ArrayList<>();for(String batch:pending){try{q.execute(batch);}catch(SQLException e){if(!Set.of(208,4121,15151).contains(e.getErrorCode()))throw e;next.add(batch);}}if(next.size()==pending.size())throw new IllegalStateException("Unresolved schema dependencies");pending=next;}
                if(!pending.isEmpty())throw new IllegalStateException("Schema incomplete");
                for(String batch:Files.readString(Path.of("database/migrations/030_khanh02_signed_checkin.sql")).split("(?im)^\\s*GO\\s*\\r?$",-1))if(!batch.isBlank())q.execute(batch);
                q.execute("ALTER ROLE tc_checkin ADD MEMBER khanh02_checkin;GRANT VIEW DEFINITION TO khanh02_checkin;");
            }
        }
    }
}
