package vn.ticketscenter.fulfillment.refund.integration;

import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** One-shot bootstrap for disposable SQL Server container CI. Refuses an existing database. */
public final class RefundCiSetup {
    public static void main(String[] args)throws Exception {
        String password=RefundFixture.required("TC_REFUND_OWNER_PASSWORD");
        if(!"sa".equals(RefundFixture.required("TC_REFUND_OWNER_USER")))throw new IllegalStateException("Disposable container owner required");
        String url=RefundFixture.required("TC_REFUND_TEST_URL");
        if(!url.startsWith("jdbc:sqlserver://127.0.0.1:")||!url.contains("databaseName=TicketCenter_Khanh03_Test;"))throw new IllegalStateException("Disposable loopback database required");
        try(var c=DriverManager.getConnection(url.replace("databaseName=TicketCenter_Khanh03_Test;","databaseName=master;"),"sa",password);var q=c.createStatement()){
            try(var r=q.executeQuery("SELECT DB_ID('TicketCenter_Khanh03_Test')")){r.next();if(r.getObject(1)!=null)throw new IllegalStateException("Refusing to reuse database");}
            q.execute("CREATE DATABASE TicketCenter_Khanh03_Test");
            for(String role:List.of("BUYER","ADMIN")){
                String login=RefundFixture.required("TC_REFUND_"+role+"_USER"),secret=RefundFixture.required("TC_REFUND_"+role+"_PASSWORD");
                if(!login.matches("khanh03_(buyer|admin)"))throw new IllegalStateException("Invalid fixture login");
                q.execute("CREATE LOGIN ["+login+"] WITH PASSWORD=N'"+secret.replace("'","''")+"',CHECK_POLICY=ON");
                q.execute("USE TicketCenter_Khanh03_Test;CREATE USER ["+login+"] FOR LOGIN ["+login+"]");
            }
        }
        try(var c=RefundFixture.owner();var q=c.createStatement()){
            q.execute("SET QUOTED_IDENTIFIER ON;SET ANSI_NULLS ON");
            var batches=new ArrayList<String>();
            try(var files=Files.list(Path.of("database/migrations"))){for(Path f:files.filter(p->p.getFileName().toString().matches("00[1-8]_.*\\.sql")).sorted().toList())batches.addAll(split(Files.readString(f)));}
            for(int pass=0;!batches.isEmpty()&&pass<8;pass++){
                var deferred=new ArrayList<String>();
                for(String batch:batches)try{q.execute(batch);}catch(SQLException e){if(!Set.of(208,4121,15151).contains(e.getErrorCode()))throw e;deferred.add(batch);}
                if(deferred.size()==batches.size())throw new IllegalStateException("Unresolved base schema dependency");batches=deferred;
            }
            if(!batches.isEmpty())throw new IllegalStateException("Incomplete base schema");
            for(String batch:split(Files.readString(Path.of("database/migrations/029_khanh03_refund_request.sql"))))q.execute(batch);
            // Current complete WAR includes Event authorization. Consume its contract unchanged
            // in this isolated fixture only, never deploy the coordination proposal to Azure.
            for(String batch:split(Files.readString(Path.of("docs/evidence/dong/DONG-DB-CONTRACT.sql"))))q.execute(batch);
            q.execute("ALTER ROLE tc_buyer ADD MEMBER khanh03_buyer;ALTER ROLE tc_platform_admin ADD MEMBER khanh03_admin");
        }
        System.out.println("Disposable refund fixture initialized with restricted logins.");
    }
    private static List<String> split(String sql){return Arrays.stream(sql.split("(?im)^\\s*GO\\s*\\r?$",-1)).filter(s->!s.isBlank()).toList();}
}
