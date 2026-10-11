package vn.ticketscenter.fulfillment.refund.integration;

import java.sql.*;
import java.util.*;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.identity.model.PlatformRole;

/** Creates UUID-only fixtures in a dedicated loopback database. Never touches shared Azure data. */
public final class RefundFixture {
    public final UUID buyer=UUID.randomUUID(),other=UUID.randomUUID(),admin=UUID.randomUUID(),org=UUID.randomUUID(),category=UUID.randomUUID(),event=UUID.randomUUID(),zone=UUID.randomUUID(),hold=UUID.randomUUID(),order=UUID.randomUUID(),payment=UUID.randomUUID(),item=UUID.randomUUID();
    public final List<UUID> tickets=List.of(UUID.randomUUID(),UUID.randomUUID());
    public final int price;
    public RefundFixture(int price,boolean past)throws SQLException {this(price,past?-86400:172800);}
    public RefundFixture(int price,int startSeconds)throws SQLException {
        this.price=price;
        try(var c=owner()){c.setAutoCommit(false);try{
            user(c,buyer,"CUSTOMER");user(c,other,"CUSTOMER");user(c,admin,"ADMIN");
            execute(c,"INSERT dbo.tc_user_platform_roles(user_id,role) VALUES(?, 'ADMIN')",admin);
            execute(c,"INSERT dbo.tc_organizations(id,name,contact_email) VALUES(?,N'Khanh refund fixture',?)",org,org+"@example.test");
            execute(c,"INSERT dbo.tc_event_categories(id,name,slug) VALUES(?,N'Refund fixture',?)",category,category.toString());
            execute(c,"""
              INSERT dbo.tc_events(id,organization_id,category_id,title,description,venue_name,venue_address,sale_start,sale_end,start_time,end_time)
              VALUES(?,?,?,N'Kiểm thử hoàn vé',N'Fixture',N'Hội trường',N'Địa chỉ',DATEADD(day,-3,SYSUTCDATETIME()),DATEADD(day,-2,SYSUTCDATETIME()),DATEADD(second,?,SYSUTCDATETIME()),DATEADD(second,?,SYSUTCDATETIME()))
              """,event,org,category,startSeconds,startSeconds+86400);
            execute(c,"INSERT dbo.tc_zones(id,event_id,name,type,price,capacity,sold_quantity) VALUES(?,?,N'Khu đứng','STANDING',?,100,2)",zone,event,price);
            execute(c,"UPDATE dbo.tc_events SET status='PUBLISHED' WHERE id=?",event);
            execute(c,"INSERT dbo.tc_ticket_holds(id,user_id,event_id,status,expires_at) VALUES(?,?,?,'CONSUMED',DATEADD(minute,10,SYSUTCDATETIME()))",hold,buyer,event);
            execute(c,"INSERT dbo.tc_orders(id,user_id,event_id,hold_id,order_code,subtotal_amount,discount_amount,total_amount,status,paid_at) VALUES(?,?,?,?,?,?,0,?,'PAID',SYSUTCDATETIME())",order,buyer,event,hold,"K03-"+order,price*2,price*2);
            if(price>0){execute(c,"INSERT dbo.tc_payments(id,order_id,txn_ref,amount,status,captured_at,provider_reference) VALUES(?,?,?,?,'CAPTURED',SYSUTCDATETIME(),N'fixture-provider')",payment,order,payment.toString(),price*2);execute(c,"UPDATE dbo.tc_orders SET accepted_payment_id=? WHERE id=?",payment,order);}
            execute(c,"INSERT dbo.tc_order_items(id,order_id,zone_id,quantity,unit_price,zone_name_snapshot) VALUES(?,?,?,2,?,N'Khu đứng')",item,order,zone,price);
            for(UUID ticket:tickets)execute(c,"INSERT dbo.tc_tickets(id,order_item_id,ticket_code,qr_secret_hash,paid_amount) VALUES(?,?,?,0x0102,?)",ticket,item,"K03-"+ticket,price);
            c.commit();
        }catch(SQLException e){c.rollback();throw e;}}
    }
    public UserPrincipal buyer(){return new UserPrincipal(buyer,PlatformRole.CUSTOMER,0);}
    public UserPrincipal other(){return new UserPrincipal(other,PlatformRole.CUSTOMER,0);}
    public UserPrincipal admin(){return new UserPrincipal(admin,PlatformRole.ADMIN,0);}
    private static void user(Connection c,UUID id,String role)throws SQLException{execute(c,"INSERT dbo.tc_users(id,email,user_name,password_hash,full_name,email_verified_at,platform_role) VALUES(?,?,?,N'fixture-only',N'Khanh fixture',SYSUTCDATETIME(),?)",id,id+"@example.test",id.toString(),role);}
    public static Connection owner()throws SQLException{return connect("OWNER");}
    public static Connection connect(String role)throws SQLException{
        String url=required("TC_REFUND_TEST_URL");if(!url.startsWith("jdbc:sqlserver://127.0.0.1:")||!url.contains("databaseName=TicketCenter_Khanh03_Test;"))throw new IllegalStateException("Dedicated loopback database required");
        return DriverManager.getConnection(url,required("TC_REFUND_"+role+"_USER"),required("TC_REFUND_"+role+"_PASSWORD"));
    }
    public static String required(String key){String v=System.getenv(key);if(v==null||v.isBlank())throw new IllegalStateException("Missing fixture setting: "+key);return v;}
    public static void execute(Connection c,String sql,Object...args)throws SQLException{try(var q=c.prepareStatement(sql)){q.setQueryTimeout(15);bind(q,args);q.execute();}}
    public static String scalar(String sql,Object...args)throws SQLException{try(var c=owner();var q=c.prepareStatement(sql)){bind(q,args);try(var r=q.executeQuery()){if(!r.next())return null;return r.getString(1);}}}
    private static void bind(PreparedStatement q,Object...args)throws SQLException{for(int i=0;i<args.length;i++)q.setObject(i+1,args[i] instanceof UUID id?id.toString():args[i]);}
}
