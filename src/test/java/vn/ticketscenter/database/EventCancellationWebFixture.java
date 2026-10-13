package vn.ticketscenter.database;

import java.nio.file.*;
import java.util.*;
import vn.ticketscenter.fulfillment.checkin.integration.ScanFixture;
import vn.ticketscenter.identity.service.PasswordHasher;

/** Complete-WAR fixtures are created only through ScanFixture's fixed loopback guard. */
public final class EventCancellationWebFixture {
    public static void main(String[] args)throws Exception {
        Path file=Path.of("target/khanh06-web-fixture.properties");
        if(args[0].equals("prepare")) {
            var f=EventCancellationSqlServerTest.fixture(true,false);var p=new Properties();
            var next=EventCancellationSqlServerTest.anotherOrder(f);
            var http=EventCancellationSqlServerTest.fixture(true,false);
            var browser=EventCancellationSqlServerTest.fixture(true,false);
            var expired=EventCancellationSqlServerTest.fixture(false,false);
            EventCancellationSqlServerTest.sql("UPDATE dbo.tc_events SET sale_start=DATEADD(day,-3,SYSUTCDATETIME()),sale_end=DATEADD(day,-2,SYSUTCDATETIME()),start_time=DATEADD(second,-1,SYSUTCDATETIME()) WHERE id=?",expired.base().event());
            p.setProperty("restartEvent",f.base().event().toString());p.setProperty("httpEvent",http.base().event().toString());
            p.setProperty("uiEvent",browser.base().event().toString());p.setProperty("expiredEvent",expired.base().event().toString());
            p.setProperty("admin",f.admin().toString());p.setProperty("buyer",f.base().user().toString());
            p.setProperty("restartOrder",next.order().toString());
            String hash=new PasswordHasher().hash(ScanFixture.required("TC_SCAN_FIXTURE_PASSWORD"));
            EventCancellationSqlServerTest.sql("UPDATE dbo.tc_users SET password_hash=? WHERE id IN(?,?)",hash,f.admin(),f.base().user());
            EventCancellationSqlServerTest.sql("UPDATE dbo.tc_events SET title=N'KHANH06 Browser Event' WHERE id=?",browser.base().event());
            // Create a committed partial batch before actual Tomcat startup, then simulate worker death.
            EventCancellationSqlServerTest.cancel(f);
            String first=EventCancellationSqlServerTest.value("SELECT TOP(1) CONVERT(varchar(36),order_id) FROM dbo.tc_cancellation_orders WHERE event_id=? ORDER BY order_id",f.base().event());
            try(var c=PaymentCrossDomainSqlTest.source("worker",null).getConnection();var q=c.createStatement()) {
                boolean result=q.execute("EXEC dbo.sp_refund_adopt_event_cancellation '"+f.base().event()+"','"+first+"'");
                while(true){if(result){try(var r=q.getResultSet()){while(r.next()){ }}}else if(q.getUpdateCount()==-1)break;result=q.getMoreResults();}
            }
            try(var out=Files.newOutputStream(file)){p.store(out,"Disposable fixture identifiers only");}return;
        }
        var p=new Properties();try(var in=Files.newInputStream(file)){p.load(in);}
        String event=p.getProperty("restartEvent");
        if(args[0].equals("verify")) {
            if(!"2".equals(EventCancellationSqlServerTest.value("SELECT processed_orders FROM dbo.tc_cancellation_jobs WHERE event_id=?",event)))System.exit(3);
            if(!"2".equals(EventCancellationSqlServerTest.value("SELECT COUNT(*) FROM dbo.tc_refund_obligations r JOIN dbo.tc_orders o ON o.id=r.order_id WHERE o.event_id=? AND r.status='COMPLETED'",event)))System.exit(3);
            if(!"2".equals(EventCancellationSqlServerTest.value("SELECT COUNT(*) FROM dbo.tc_refund_transfer_logs l JOIN dbo.tc_refund_obligations r ON r.id=l.refund_id JOIN dbo.tc_orders o ON o.id=r.order_id WHERE o.event_id=?",event)))throw new IllegalStateException("Duplicate cancellation transfers");
            if(!"0".equals(EventCancellationSqlServerTest.value("SELECT SUM(sold_quantity) FROM dbo.tc_zones WHERE event_id=?",event)))throw new IllegalStateException("Incorrect cancellation inventory");
            System.out.println("Actual Tomcat resumed a partial cancellation batch; two obligations and transfers, inventory returned once.");
        }else throw new IllegalArgumentException("Unsupported fixture mode");
    }
}
