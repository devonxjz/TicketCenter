package vn.ticketscenter.settlement.infrastructure;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.model.OrganizationRole;
import vn.ticketscenter.identity.security.UserPrincipal;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FinanceAuthorizationTest {
    private final UUID actor = UUID.randomUUID(), org = UUID.randomUUID();
    private UserPrincipal principal() { return new UserPrincipal(actor, PlatformRole.ADMIN, 7, OrganizationRole.MANAGER); }
    @Test void currentAuthorityOverridesSessionAdminRole() throws Exception {
        try (var db = connection("ACTIVE", 7, "CUSTOMER", null)) {
            assertEquals(403, assertThrows(FinanceException.class, () -> FinanceAuthorization.requireAdmin(db, principal())).status());
        }
    }
    @Test void disabledAndStaleSessionsAreUnauthorized() throws Exception {
        for (var row : List.of(new Object[]{"DISABLED",7,"ADMIN"}, new Object[]{"ACTIVE",8,"ADMIN"})) {
            try (var db = connection((String)row[0], (Integer)row[1], (String)row[2], null)) {
                assertEquals(401, assertThrows(FinanceException.class, () -> FinanceAuthorization.requireAdmin(db, principal())).status());
            }
        }
    }
    @Test void activeAdminMayReadAnyOrganization() throws Exception {
        try (var db = connection("ACTIVE",7,"ADMIN",null)) { FinanceAuthorization.requireManager(db, principal(), org); }
    }
    @Test void managerAuthorityMustMatchRequestedOrganization() throws Exception {
        try (var db = connection("ACTIVE",7,"CUSTOMER",org)) { FinanceAuthorization.requireManager(db, principal(),org); }
        try (var db = connection("ACTIVE",7,"CUSTOMER",org)) {
            assertEquals(403,assertThrows(FinanceException.class, () -> FinanceAuthorization.requireManager(db, principal(), UUID.randomUUID())).status());
        }
    }
    @Test void nullSessionIsRejectedBeforeDatabaseUse() {
        assertEquals(401,assertThrows(FinanceException.class, () -> FinanceAuthorization.requireAdmin(null,null)).status());
    }
    // JDBC contract double only; dedicated SQL tests prove actual permissions.
    private Connection connection(String status, int version, String role, UUID membership) {
        return proxy(Connection.class, (method,args) -> {
            if (method.equals("prepareStatement")) {
                boolean user = ((String)args[0]).contains("tc_users");
                var params = new HashMap<Integer,Object>();
                return proxy(PreparedStatement.class,(name,values) -> {
                    if(name.equals("setString") || name.equals("setObject")) { params.put((Integer)values[0],values[1]); return null; }
                    if(name.equals("executeQuery")) {
                        boolean allowed = user || Objects.equals(String.valueOf(membership),params.get(2));
                        boolean[] before = {true};
                        return proxy(ResultSet.class,(op,vs) -> switch(op) {
                            case "next" -> { boolean result=before[0] && allowed; before[0]=false; yield result; }
                            case "getString" -> switch((String)vs[0]) { case "status" -> status; case "platform_role" -> role; default -> "MANAGER"; };
                            case "getInt" -> version;
                            default -> null;
                        });
                    }
                    return null;
                });
            }
            return null;
        });
    }
    interface Call { Object invoke(String name,Object[] args) throws Throwable; }
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type, Call call) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(p,m,args) -> call.invoke(m.getName(),args));
    }
}
