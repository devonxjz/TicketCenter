package vn.ticketscenter.identity.dao;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.hibernate.jdbc.ReturningWork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.identity.dao.OrganizationMembershipRepository.MembershipRow;
import vn.ticketscenter.identity.model.OrganizationRole;

import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Binding, row mapping and error translation of the membership procedures, without a database. */
class OrganizationMembershipProceduresTest {

    private final OrganizationMembershipRepository repository = new OrganizationMembershipRepository();
    private final UUID org = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();
    private final UUID membership = UUID.randomUUID();

    private final List<String> preparedSql = new ArrayList<>();
    private final List<String> calls = new ArrayList<>();
    private int clears;
    private SQLException failure;
    private boolean emptyResult;
    private Map<String, Object> row;

    @BeforeEach
    void setUp() {
        preparedSql.clear();
        calls.clear();
        clears = 0;
        failure = null;
        emptyResult = false;
        row = Map.of("membership_id", membership.toString(), "user_id", user.toString(), "role", "CHECK_IN_STAFF",
                "active", true, "joined_at", LocalDateTime.of(2026, 10, 1, 8, 30, 15, 0));
    }

    @Test
    void addMemberBindsTheFourArgumentsInProcedureOrderAndMapsTheRow() {
        MembershipRow result = repository.addMember(em(), org, actor, user, OrganizationRole.CHECK_IN_STAFF);

        assertEquals(List.of("{call dbo.usp_AddMember(?,?,?,?)}"), preparedSql);
        assertEquals(List.of("1=" + org, "2=" + actor, "3=" + user, "4=CHECK_IN_STAFF"), calls);
        assertEquals(new MembershipRow(membership, user, OrganizationRole.CHECK_IN_STAFF, true,
                Instant.parse("2026-10-01T08:30:15Z")), result);
        assertEquals(1, clears, "the persistence context is cleared after a procedure call");
    }

    @Test
    void changeMemberBindsNullForUnchangedRoleAndActive() {
        repository.changeMember(em(), org, actor, user, null, null);

        assertEquals(List.of("{call dbo.usp_ChangeMemberRole(?,?,?,?,?)}"), preparedSql);
        assertEquals(List.of("1=" + org, "2=" + actor, "3=" + user, "4=NULL(" + Types.VARCHAR + ")",
                "5=NULL(" + Types.BIT + ")"), calls);
    }

    @Test
    void changeMemberBindsRoleAndActiveWhenGiven() {
        repository.changeMember(em(), org, actor, user, OrganizationRole.MANAGER, Boolean.FALSE);

        assertEquals(List.of("1=" + org, "2=" + actor, "3=" + user, "4=MANAGER", "5=false"), calls);
    }

    @Test
    void addMemberRejectsMissingRoleBeforeTouchingTheDatabase() {
        assertThrows(NullPointerException.class, () -> repository.addMember(em(), org, actor, user, null));
        assertTrue(preparedSql.isEmpty());
    }

    @Test
    void emptyResultIsAnError() {
        emptyResult = true;
        assertThrows(IllegalStateException.class, () -> repository.addMember(em(), org, actor, user, OrganizationRole.MANAGER));
    }

    @Test
    void persistenceContextIsClearedEvenWhenTheProcedureFails() {
        failure = new SQLException("Member not found", "S0001", 52651);
        assertThrows(IllegalArgumentException.class, () -> repository.changeMember(em(), org, actor, user, null, Boolean.TRUE));
        assertEquals(1, clears);
    }

    @Test
    void procedureFailuresBecomeTheExceptionsTheServletMaps() {
        SecurityException forbidden = assertThrows(SecurityException.class, () -> translate(51810));
        assertTrue(forbidden.getMessage().startsWith("FORBIDDEN"));

        assertTrue(assertThrows(IllegalArgumentException.class, () -> translate(51816)).getMessage().contains("MANAGER"));
        assertInstanceOf(IllegalStateException.class, assertThrows(IllegalStateException.class, () -> translate(51817)));
        assertTrue(assertThrows(IllegalStateException.class, () -> translate(52650)).getMessage()
                .startsWith("MEMBER_ALREADY_EXISTS"));
        assertInstanceOf(IllegalArgumentException.class, assertThrows(IllegalArgumentException.class, () -> translate(52651)));
        assertInstanceOf(IllegalArgumentException.class, assertThrows(IllegalArgumentException.class, () -> translate(52652)));
        assertTrue(assertThrows(IllegalStateException.class, () -> translate(51106)).getMessage()
                .startsWith("CANNOT_DEACTIVATE_LAST_MANAGER"));
    }

    @Test
    void theNumberIsFoundOnAChainedException() {
        SQLException outer = new SQLException("batch failed", "S0001", 0);
        outer.setNextException(new SQLException("Organization must retain an active manager", "S0001", 51106));

        assertTrue(assertThrows(IllegalStateException.class, () -> {
            throw OrganizationMembershipRepository.translate(outer);
        }).getMessage().startsWith("CANNOT_DEACTIVATE_LAST_MANAGER"));
    }

    @Test
    void unknownFailuresKeepTheirCause() {
        SQLException unexpected = new SQLException("deadlock victim", "40001", 1205);

        IllegalStateException wrapped = assertThrows(IllegalStateException.class, () -> {
            throw OrganizationMembershipRepository.translate(unexpected);
        });
        assertSame(unexpected, wrapped.getCause());
    }

    private void translate(int code) {
        failure = new SQLException("failure " + code, "S0001", code);
        repository.changeMember(em(), org, actor, user, OrganizationRole.MANAGER, null);
    }

    // ---- JDBC doubles -------------------------------------------------------------------------------------

    private EntityManager em() {
        Session session = proxy(Session.class, (p, m, a) -> {
            if (m.getName().equals("doReturningWork")) {
                @SuppressWarnings("unchecked") ReturningWork<Object> work = (ReturningWork<Object>) a[0];
                try {
                    return work.execute(connection());
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
            }
            return defaultValue(m.getReturnType());
        });
        return proxy(EntityManager.class, (p, m, a) -> switch (m.getName()) {
            case "unwrap" -> session;
            case "clear" -> {
                clears++;
                yield null;
            }
            default -> defaultValue(m.getReturnType());
        });
    }

    private Connection connection() {
        return proxy(Connection.class, (p, m, a) -> {
            if (m.getName().equals("prepareCall")) {
                preparedSql.add((String) a[0]);
                return callableStatement();
            }
            return defaultValue(m.getReturnType());
        });
    }

    private CallableStatement callableStatement() {
        return proxy(CallableStatement.class, (p, m, a) -> {
            switch (m.getName()) {
                case "setString", "setBoolean" -> calls.add(a[0] + "=" + a[1]);
                case "setNull" -> calls.add(a[0] + "=NULL(" + a[1] + ")");
                case "executeQuery" -> {
                    if (failure != null) throw failure;
                    return resultSet();
                }
                default -> {
                    return defaultValue(m.getReturnType());
                }
            }
            return null;
        });
    }

    private ResultSet resultSet() {
        boolean[] consumed = {false};
        return proxy(ResultSet.class, (p, m, a) -> switch (m.getName()) {
            case "next" -> {
                boolean has = !emptyResult && !consumed[0];
                consumed[0] = true;
                yield has;
            }
            case "getString" -> String.valueOf(row.get(a[0]));
            case "getBoolean" -> row.get(a[0]);
            case "getObject" -> row.get(a[0]);
            default -> defaultValue(m.getReturnType());
        });
    }

    @FunctionalInterface
    private interface Handler {
        Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler::invoke);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }
}
