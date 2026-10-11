package vn.ticketscenter.fulfillment.refund.integration;
import java.nio.file.*;
import java.util.*;
import vn.ticketscenter.identity.service.PasswordHasher;
import static vn.ticketscenter.fulfillment.refund.integration.RefundFixture.*;
/** Fixture-only login provisioning for actual Tomcat/JSP acceptance. */
public final class RefundWebFixture {
    public static void main(String[] args)throws Exception{
        var fixture=new RefundFixture(160001,false);String password="K03Fixture!25";String hash=new PasswordHasher().hash(password);
        try(var c=owner()){for(UUID user:List.of(fixture.buyer,fixture.other,fixture.admin))execute(c,"UPDATE dbo.tc_users SET password_hash=? WHERE id=?",hash,user);}
        var properties=new Properties();properties.setProperty("buyer",fixture.buyer.toString());properties.setProperty("other",fixture.other.toString());properties.setProperty("admin",fixture.admin.toString());properties.setProperty("order",fixture.order.toString());properties.setProperty("tickets",fixture.tickets.getFirst()+","+fixture.tickets.getLast());
        Files.createDirectories(Path.of("target"));try(var writer=Files.newBufferedWriter(Path.of("target/khanh03-web-fixture.properties"))){properties.store(writer,"Disposable local fixture identities; no project credentials");}
    }
}
