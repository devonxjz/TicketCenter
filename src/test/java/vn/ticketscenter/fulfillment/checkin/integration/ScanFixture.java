package vn.ticketscenter.fulfillment.checkin.integration;
import java.sql.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import vn.ticketscenter.identity.service.PasswordHasher;
/** Fresh UUID fixtures, only in the named disposable loopback SQL database. */
public final class ScanFixture {
    public final UUID staff=UUID.randomUUID(),manager=UUID.randomUUID(),buyer=UUID.randomUUID(),other=UUID.randomUUID(),org=UUID.randomUUID(),event=UUID.randomUUID(),category=UUID.randomUUID(),zone=UUID.randomUUID(),hold=UUID.randomUUID(),order=UUID.randomUUID(),item=UUID.randomUUID();
    public final List<UUID> tickets=List.of(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
    public ScanFixture()throws Exception{
        try(var c=owner()){c.setAutoCommit(false);try{
            String hash=new PasswordHasher().hash(required("TC_SCAN_FIXTURE_PASSWORD"));
            for(UUID user:List.of(staff,manager,buyer,other))execute(c,"INSERT dbo.tc_users(id,email,user_name,password_hash,full_name,email_verified_at,platform_role) VALUES(?,?,?,?,N'Nhân viên Khánh 02',SYSUTCDATETIME(),'CUSTOMER')",user,user+"@fixture.invalid",user.toString(),hash);
            execute(c,"INSERT dbo.tc_organizations(id,name,contact_email,requester_id) VALUES(?,N'KHANH-02 Fixture',?,?)",org,org+"@fixture.invalid",manager);
            for(UUID user:List.of(staff,manager))execute(c,"INSERT dbo.tc_organization_memberships(id,user_id,organization_id,role,active) VALUES(?,?,?,?,1)",UUID.randomUUID(),user,org,user.equals(manager)?"MANAGER":"CHECK_IN_STAFF");
            execute(c,"INSERT dbo.tc_event_categories(id,name,slug) VALUES(?,N'KHANH-02 Fixture',?)",category,category.toString());
            execute(c,"INSERT dbo.tc_events(id,organization_id,category_id,title,description,venue_name,venue_address,sale_start,sale_end,start_time,end_time) VALUES(?,?,?,N'KHANH-02 · Đêm nhạc',N'Fixture',N'Hội trường A',N'Địa chỉ',DATEADD(day,-3,SYSUTCDATETIME()),DATEADD(day,-2,SYSUTCDATETIME()),DATEADD(minute,30,SYSUTCDATETIME()),DATEADD(hour,2,SYSUTCDATETIME()))",event,org,category);
            execute(c,"INSERT dbo.tc_zones(id,event_id,name,type,price,capacity,sold_quantity) VALUES(?,?,N'Khu A','STANDING',0,100,4)",zone,event);
            execute(c,"UPDATE dbo.tc_events SET status='PUBLISHED' WHERE id=?",event);
            execute(c,"INSERT dbo.tc_ticket_holds(id,user_id,event_id,status,expires_at) VALUES(?,?,?,'CONSUMED',DATEADD(minute,10,SYSUTCDATETIME()))",hold,buyer,event);
            execute(c,"INSERT dbo.tc_orders(id,user_id,event_id,hold_id,order_code,subtotal_amount,discount_amount,total_amount,status,paid_at) VALUES(?,?,?,?,?,0,0,0,'PAID',SYSUTCDATETIME())",order,buyer,event,hold,"K02-"+order);
            execute(c,"INSERT dbo.tc_order_items(id,order_id,zone_id,quantity,unit_price,zone_name_snapshot) VALUES(?,?,?,4,0,N'Khu A')",item,order,zone);
            for(UUID ticket:tickets)execute(c,"INSERT dbo.tc_tickets(id,order_item_id,ticket_code,qr_secret_hash,paid_amount) VALUES(?,?,?,0x01020304,0)",ticket,item,"K02-"+ticket);
            c.commit();
        }catch(Exception e){c.rollback();throw e;}}
    }
    public String qr(UUID ticket)throws Exception{
        byte[] bytes=("{\"id\":\""+ticket+"\",\"name\":\"Khách mời Khánh 02\"}").getBytes(StandardCharsets.UTF_8);
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(required("TC_TICKET_SIGNING_SECRET").getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        var b64=Base64.getUrlEncoder().withoutPadding();return b64.encodeToString(bytes)+"."+b64.encodeToString(mac.doFinal(bytes));
    }
    public static Connection owner()throws SQLException{return connect("OWNER");}
    public static Connection connect(String role)throws SQLException{
        String url=required("TC_SCAN_TEST_URL");if(!url.startsWith("jdbc:sqlserver://127.0.0.1:")||!url.contains("databaseName=TicketCenter_Khanh02_Test;"))throw new IllegalStateException("Dedicated loopback database required");
        return DriverManager.getConnection(url,required("TC_SCAN_"+role+"_USER"),required("TC_SCAN_"+role+"_PASSWORD"));
    }
    public static String required(String key){String value=System.getenv(key);if(value==null||value.isBlank())throw new IllegalStateException("Missing fixture setting: "+key);return value;}
    public static void execute(Connection c,String sql,Object...args)throws SQLException{try(var q=c.prepareStatement(sql)){bind(q,args);q.setQueryTimeout(15);q.execute();}}
    public static String scalar(String sql,Object...args)throws SQLException{try(var c=owner();var q=c.prepareStatement(sql)){bind(q,args);try(var r=q.executeQuery()){r.next();return r.getString(1);}}}
    private static void bind(PreparedStatement q,Object...args)throws SQLException{for(int i=0;i<args.length;i++)q.setObject(i+1,args[i] instanceof UUID id?id.toString():args[i]);}
}
