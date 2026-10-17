package vn.ticketscenter.event.dao;

import jakarta.persistence.*;
import org.hibernate.Session;
import org.hibernate.jdbc.ReturningWork;
import vn.ticketscenter.config.persistence.*;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.Map;

/** Supplies the real repository with one caller-owned JDBC connection for read-only tests. */
public final class EventRepositoryFixture {
    private EventRepositoryFixture() { }
    public static EventCatalogRepository repository(Connection connection) {
        var transaction = proxy(EntityTransaction.class, (method, args) -> switch (method) {
            case "isActive" -> false;
            default -> null;
        });
        var session = proxy(Session.class, (method, args) -> {
            if (method.equals("doReturningWork")) return ((ReturningWork<?>) args[0]).execute(connection);
            return null;
        });
        var manager = proxy(EntityManager.class, (method, args) -> switch (method) {
            case "getTransaction" -> transaction;
            case "unwrap" -> session;
            default -> null;
        });
        var factory = proxy(EntityManagerFactory.class, (method, args) -> method.equals("createEntityManager") ? manager : null);
        return new EventCatalogRepository(new TransactionManager(Map.of(DatabasePrincipal.BUYER, factory)));
    }
    @FunctionalInterface private interface Invocation { Object call(String method, Object[] args) throws Throwable; }
    private static <T> T proxy(Class<T> type, Invocation work) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (instance, method, args) -> work.call(method.getName(), args)));
    }
}
