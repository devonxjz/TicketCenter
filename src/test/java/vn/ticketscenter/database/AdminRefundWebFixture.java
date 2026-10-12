package vn.ticketscenter.database;

import java.util.*;
import java.nio.file.*;
import vn.ticketscenter.identity.service.PasswordHasher;
import vn.ticketscenter.fulfillment.checkin.integration.ScanFixture;

/** Creates only disposable fixtures in the guarded loopback SQL database. */
public final class AdminRefundWebFixture {
    public static void main(String[] args)throws Exception {
        String secret=ScanFixture.required("TC_SCAN_FIXTURE_PASSWORD"),hash=new PasswordHasher().hash(secret);
        var properties=new Properties();var admin=AdminRefundDecisionSqlServerTest.fixture(false);
        properties.setProperty("admin",admin.admin().toString());properties.setProperty("approve",admin.refund().toString());
        try(var c=ScanFixture.owner()){for(UUID user:List.of(admin.admin(),admin.buyer()))ScanFixture.execute(c,"UPDATE dbo.tc_users SET password_hash=? WHERE id=?",hash,user);}
        for(String name:List.of("reject","retry","retryEmpty","unknown","uiApprove","uiReject","uiRetry")){
            var f=AdminRefundDecisionSqlServerTest.fixture(false);properties.setProperty(name,f.refund().toString());
            if(name.equals("uiApprove"))try(var c=ScanFixture.owner()){ScanFixture.execute(c,"UPDATE dbo.tc_users SET full_name=? WHERE id=?","<img src=x onerror=\"window.__refundXss=1\">",f.buyer());}
            if(List.of("retry","retryEmpty","unknown","uiRetry").contains(name)){
                var result=f.service().decide(f.actor(),f.refund(),new vn.ticketscenter.fulfillment.refund.dto.AdminRefundDecisionRequestDto("APPROVE",null,null),UUID.randomUUID());
                AdminRefundDecisionSqlServerTest.outcome(result.attemptId(),name.equals("unknown")?"UNKNOWN":"FAILED");
            }
        }
        properties.setProperty("buyer",admin.buyer().toString());
        try(var writer=Files.newBufferedWriter(Path.of("target/khanh04-web-fixture.properties"))){properties.store(writer,"Disposable fixture identities; no secrets");}
    }
}
