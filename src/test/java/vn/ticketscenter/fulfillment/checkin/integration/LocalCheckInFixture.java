package vn.ticketscenter.fulfillment.checkin.integration;

import jakarta.persistence.Persistence;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import vn.ticketscenter.identity.model.*;
import vn.ticketscenter.event.model.*;
import vn.ticketscenter.order.model.*;
import vn.ticketscenter.fulfillment.model.*;
import vn.ticketscenter.ticketing.model.*;
import vn.ticketscenter.config.DatabaseConfig;
import vn.ticketscenter.identity.service.PasswordHasher;

/** Opt-in fixture tool. Never points at the configured shared/Azure database. */
public final class LocalCheckInFixture {
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("ddl")) {
            var settings = new HashMap<String,Object>();
            settings.put("hibernate.hbm2ddl.auto", "none");
            settings.put("hibernate.boot.allow_jdbc_metadata_access", false);
            settings.put("jakarta.persistence.schema-generation.database.action", "none");
            settings.put("jakarta.persistence.schema-generation.scripts.action", "create");
            settings.put("jakarta.persistence.schema-generation.scripts.create-target", "target/khanh01-model-schema.sql");
            Persistence.createEntityManagerFactory("ticketscenter",settings).close();
            return;
        }
        if (!DatabaseConfig.getDatabaseName().equals("TicketCenter_Khanh01_Test")
                || !DatabaseConfig.getHost().equals("127.0.0.1"))
            throw new IllegalStateException("Fixture writes are restricted to the dedicated local database");
        var settings = Map.<String,Object>of("jakarta.persistence.jdbc.url",DatabaseConfig.getJdbcUrl(),
                "jakarta.persistence.jdbc.user",DatabaseConfig.getUser(),
                "jakarta.persistence.jdbc.password",DatabaseConfig.getPassword());
        try(var factory=Persistence.createEntityManagerFactory("ticketscenter",settings)) {
            var em=factory.createEntityManager();
            var tx=em.getTransaction(); tx.begin();
            try {
                if(args.length==1 && args[0].equals("tickets")) {
                    var ids=new Properties();try(var in=Files.newInputStream(Path.of("docs/handoff/khanh01-fixture.properties"))){ids.load(in);}
                    var event=em.find(Event.class,UUID.fromString(ids.getProperty("TC_CHECKIN_TEST_EVENT_ID")));
                    var buyer=em.find(User.class,UUID.fromString(ids.getProperty("TC_CHECKIN_TEST_MANAGER_ID")));
                    if(((Number)em.createNativeQuery("SELECT COUNT(*) FROM dbo.tc_orders").getSingleResult()).intValue()!=0)
                        throw new IllegalStateException("Ticket fixture already exists");
                    var zone=new Zone(event,"Fixture Standing",ZoneType.STANDING,100000);set(zone,"standingCapacity",100);em.persist(zone);
                    var hold=new TicketHold(buyer,event,Instant.now().plusSeconds(600));em.persist(hold);
                    var order=new Order(buyer,event,hold,"KHANH01-FIXTURE-ORDER",300000,0,300000);em.persist(order);
                    var item=new OrderItem(order,zone,null,"Fixture Standing",null,3,100000);em.persist(item);
                    for(int i=0;i<3;i++) {
                        var ticket=new Ticket(item,"KHANH01-FIXTURE-TICKET-"+i,100000);
                        if(i==0){set(ticket,"status",TicketStatus.USED);set(ticket,"usedAt",Instant.now());}
                        em.persist(ticket);
                    }
                    tx.commit();return;
                }
                if (((Number)em.createNativeQuery("SELECT COUNT(*) FROM dbo.tc_users").getSingleResult()).intValue()!=0)
                    throw new IllegalStateException("Refusing to seed a nonempty database");
                String password=System.getenv("TC_CHECKIN_FIXTURE_PASSWORD");
                if(password==null) throw new IllegalStateException("Fixture password must be supplied privately");
                var hasher=new PasswordHasher();
                var manager=new User("manager@khanh01.invalid","khanh01-manager","Khánh 01 Manager",hasher.hash(password));
                var staff=new User("staff@khanh01.invalid","khanh01-staff","Khánh 01 Staff",hasher.hash(password));
                var outsider=new User("outsider@khanh01.invalid","khanh01-outsider","Khánh 01 Outsider",hasher.hash(password));
                for(var user:List.of(manager,staff,outsider)){ user.verifyEmail();em.persist(user); }
                var org=new Organization("KHANH-01 Fixture A","org-a@khanh01.invalid",manager); org.approve();em.persist(org);
                var other=new Organization("KHANH-01 Fixture B","org-b@khanh01.invalid",outsider); other.approve();em.persist(other);
                em.persist(new OrganizationMembership(manager,org,OrganizationRole.MANAGER));
                em.persist(new OrganizationMembership(staff,org,OrganizationRole.CHECK_IN_STAFF));
                em.persist(new OrganizationMembership(outsider,other,OrganizationRole.CHECK_IN_STAFF));
                Instant now=Instant.now();
                var open=event(org,"KHANH-01 • Đang mở",now.plusSeconds(1800),now.plusSeconds(7200),EventStatus.PUBLISHED);
                var early=event(org,"KHANH-01 • Chưa tới giờ",now.plusSeconds(7200),now.plusSeconds(10800),EventStatus.PUBLISHED);
                var cancelled=event(org,"KHANH-01 • Đã hủy",now.plusSeconds(1800),now.plusSeconds(7200),EventStatus.CANCELLED);
                var ended=event(org,"KHANH-01 • Đã kết thúc",now.minusSeconds(7200),now.minusSeconds(60),EventStatus.PUBLISHED);
                var foreign=event(other,"KHANH-01 • Org B",now.plusSeconds(1800),now.plusSeconds(7200),EventStatus.PUBLISHED);
                for(var event:List.of(open,early,cancelled,ended,foreign))em.persist(event);
                em.flush();
                var properties=new Properties();
                properties.setProperty("TC_CHECKIN_TEST_EVENT_ID",open.getId().toString());
                properties.setProperty("TC_CHECKIN_TEST_EARLY_ID",early.getId().toString());
                properties.setProperty("TC_CHECKIN_TEST_CANCELLED_ID",cancelled.getId().toString());
                properties.setProperty("TC_CHECKIN_TEST_ENDED_ID",ended.getId().toString());
                properties.setProperty("TC_CHECKIN_TEST_FOREIGN_ID",foreign.getId().toString());
                properties.setProperty("TC_CHECKIN_TEST_ACTOR_ID",staff.getId().toString());
                properties.setProperty("TC_CHECKIN_TEST_MANAGER_ID",manager.getId().toString());
                properties.setProperty("TC_CHECKIN_TEST_OUTSIDER_ID",outsider.getId().toString());
                properties.setProperty("TC_CHECKIN_TEST_ORG_ID",org.getId().toString());
                tx.commit();
                Files.createDirectories(Path.of("docs/handoff"));
                try(var out=Files.newOutputStream(Path.of("docs/handoff/khanh01-fixture.properties"))){properties.store(out,"Dedicated local acceptance fixture IDs");}
            } catch(Exception e) {if(tx.isActive())tx.rollback();throw e;} finally {em.close();}
        }
    }
    private static Event event(Organization org,String title,Instant start,Instant end,EventStatus status)throws Exception {
        var event=new Event(org,title,"Local check-in acceptance",EventCategory.values()[0],"Nhà hát kiểm thử","Local fixture");
        set(event,"startTime",start);set(event,"endTime",end);set(event,"saleStart",start.minusSeconds(86400));
        set(event,"saleEnd",start);set(event,"status",status);return event;
    }
    private static void set(Object target,String name,Object value)throws Exception {
        var field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);
    }
}
