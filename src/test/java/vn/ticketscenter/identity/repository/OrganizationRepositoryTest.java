package vn.ticketscenter.identity.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.model.Organization;
import vn.ticketscenter.identity.model.OrganizationStatus;
import vn.ticketscenter.identity.model.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.extension.ExtendWith(vn.ticketscenter.platform.testsupport.ExternalTestCondition.class)
public class OrganizationRepositoryTest {

    private final OrganizationRepository repository = new OrganizationRepository();
    private final UserRepository userRepository = new UserRepository();

    @Test
    @DisplayName("Verify null guards throw NullPointerException")
    void testNullGuards() {
        assertThrows(NullPointerException.class, () -> repository.findById(null, UUID.randomUUID()));
        assertThrows(NullPointerException.class, () -> repository.findByRequesterId(null, UUID.randomUUID()));
        assertThrows(NullPointerException.class, () -> repository.hasPendingRequest(null, UUID.randomUUID()));
        assertThrows(NullPointerException.class, () -> repository.findByStatus(null, null, 0, 10));
        assertThrows(NullPointerException.class, () -> repository.countByStatus(null, null));
        assertThrows(NullPointerException.class, () -> repository.save(null, null));
    }

    @Test
    @org.junit.jupiter.api.Tag("db")
    @DisplayName("Verify repository integration with real database")
    void testRepositoryIntegration() {
        try (PersistenceRegistry registry = new PersistenceRegistry()) {
            registry.initialize();
            if (!registry.isReady() || !registry.hasPrincipal(DatabasePrincipal.ADMIN)) {
                Assumptions.assumeTrue(false, "Database is not available for integration testing.");
            }
            TransactionManager tm = registry.transactionManager();

            String suffix = UUID.randomUUID().toString().substring(0, 8);
            String email = "org.req." + suffix + "@ticketscenter.vn";
            String userName = "req" + suffix;

            User requester = tm.execute(DatabasePrincipal.AUTH, em -> {
                User user = new User(email, userName, "Org Requester", "hash12345678");
                userRepository.save(em, user);
                return user;
            });

            Organization saved = tm.execute(DatabasePrincipal.ADMIN, em -> {
                Organization org = new Organization(
                        "Test Org " + suffix,
                        "contact." + suffix + "@ticketscenter.vn",
                        "0900000000",
                        "Integration test organization",
                        requester
                );
                org.submitForApproval();
                return repository.save(em, org);
            });

            assertNotNull(saved.getId());

            tm.execute(DatabasePrincipal.ADMIN, em -> {
                Optional<Organization> found = repository.findById(em, saved.getId());
                assertTrue(found.isPresent());
                assertEquals("Test Org " + suffix, found.get().getName());

                List<Organization> list = repository.findByRequesterId(em, requester.getId());
                assertFalse(list.isEmpty());
                assertEquals(saved.getId(), list.getFirst().getId());

                boolean hasPending = repository.hasPendingRequest(em, requester.getId());
                assertTrue(hasPending);

                List<Organization> pendingList = repository.findByStatus(em, OrganizationStatus.PENDING_APPROVAL, 0, 100);
                assertFalse(pendingList.isEmpty());
                assertTrue(pendingList.stream().anyMatch(o -> o.getId().equals(saved.getId())));

                long count = repository.countByStatus(em, OrganizationStatus.PENDING_APPROVAL);
                assertTrue(count > 0);

                return null;
            });
        }
    }
}
