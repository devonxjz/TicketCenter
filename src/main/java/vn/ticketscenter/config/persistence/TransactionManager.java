package vn.ticketscenter.config.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public final class TransactionManager {

    private final Map<DatabasePrincipal, EntityManagerFactory> factories;

    public TransactionManager(Map<DatabasePrincipal, EntityManagerFactory> factories) {
        this.factories = Map.copyOf(factories);
    }

    public <T> T execute(DatabasePrincipal principal, Function<EntityManager, T> work) {
        EntityManagerFactory factory = factories.get(Objects.requireNonNull(principal));
        if (factory == null) {
            throw new IllegalArgumentException("Database principal is not configured: " + principal);
        }
        EntityManager entityManager = factory.createEntityManager();
        EntityTransaction transaction = entityManager.getTransaction();
        try {
            transaction.begin();
            T result = work.apply(entityManager);
            entityManager.flush();
            transaction.commit();
            return result;
        } catch (RuntimeException exception) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw exception;
        } finally {
            entityManager.close();
        }
    }
}
