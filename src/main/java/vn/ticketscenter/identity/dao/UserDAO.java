package vn.ticketscenter.identity.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import vn.ticketscenter.identity.model.User;

import java.util.Objects;
import java.util.Optional;

public class UserDAO {
    private final EntityManager entityManager;

    public UserDAO(EntityManager entityManager) {
        this.entityManager = Objects.requireNonNull(entityManager);
    }

    public Optional<User> findByNormalizedEmail(String email) {
        try {
            User user = entityManager.createQuery(
                    "SELECT u FROM User u WHERE LOWER(u.email) = :email", User.class)
                    .setParameter("email", email.toLowerCase())
                    .getSingleResult();
            return Optional.of(user);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    public void save(User user) {
        if (user.getId() == null) {
            entityManager.persist(user);
        } else {
            entityManager.merge(user);
        }
    }
}
