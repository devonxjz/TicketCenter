package vn.ticketscenter.dashboard.service;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.settlement.service.FinanceException;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class DashboardServiceTest {
    @Test void currentAdminCheckedBeforeAnyAggregation(){boolean[] first={true};Connection db=(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},(p,m,a)->{
        if(m.getName().equals("prepareStatement")){assertTrue(((String)a[0]).contains("vw_ActorAuthority"),"aggregation before authority");return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class[]{PreparedStatement.class},(q,n,b)->{
            if(n.getName().equals("executeQuery"))return Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class[]{ResultSet.class},(r,o,c)->switch(o.getName()){case "next"->{boolean v=first[0];first[0]=false;yield v;}case "getString"->c[0].equals("status")?"ACTIVE":"CUSTOMER";case "getInt"->1;default->null;});return null;});}return null;});
        assertEquals(403,assertThrows(FinanceException.class,()->new DashboardServiceImpl(()->db).admin(new UserPrincipal(UUID.randomUUID(),PlatformRole.ADMIN,1))).status());}
}
