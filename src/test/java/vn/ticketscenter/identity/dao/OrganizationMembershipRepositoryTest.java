package vn.ticketscenter.identity.dao;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.dto.MembershipSummaryDto;
import vn.ticketscenter.identity.dto.OrganizationMemberDto;
import vn.ticketscenter.identity.model.Organization;
import vn.ticketscenter.identity.model.OrganizationMembership;
import vn.ticketscenter.identity.model.OrganizationRole;
import vn.ticketscenter.identity.model.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.extension.ExtendWith(vn.ticketscenter.platform.testsupport.ExternalTestCondition.class)
public class OrganizationMembershipRepositoryTest {

    private final OrganizationMembershipRepository repository = new OrganizationMembershipRepository();
    private final OrganizationRepository orgRepository = new OrganizationRepository();
    private final UserRepository userRepository = new UserRepository();

    @Test
    @DisplayName("Verify null guards throw NullPointerException when em is null")
    void testNullGuards() {
        assertThrows(NullPointerException.class, () -> repository.findByOrgAndUser(null, UUID.randomUUID(), UUID.randomUUID()));
        assertThrows(NullPointerException.class, () -> repository.findById(null, UUID.randomUUID()));
        assertThrows(NullPointerException.class, () -> repository.findMembersByOrgId(null, UUID.randomUUID()));
        assertThrows(NullPointerException.class, () -> repository.findMembershipsByUserId(null, UUID.randomUUID()));
        assertThrows(NullPointerException.class, () -> repository.addMember(null, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), OrganizationRole.MANAGER));
        assertThrows(NullPointerException.class, () -> repository.changeMember(null, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), null, Boolean.TRUE));
    }

    @Test
    @org.junit.jupiter.api.Tag("db")
    @DisplayName("Verify OrganizationMembershipRepository integration with real database")
    void testRepositoryIntegration() {
        try (PersistenceRegistry registry = new PersistenceRegistry()) {
            registry.initialize();
            if (!registry.isReady() || !registry.hasPrincipal(DatabasePrincipal.ADMIN)) {
                Assumptions.assumeTrue(false, "Database is not available for integration testing.");
            }
            TransactionManager tm = registry.transactionManager();

            String suffix = UUID.randomUUID().toString().substring(0, 8);
            String email = "member." + suffix + "@ticketscenter.vn";
            String userName = "mem" + suffix;

            User user = tm.execute(DatabasePrincipal.AUTH, em -> {
                User u = userRepository.findById(em, userRepository.register(em, email, userName, "Member User " + suffix, "hash12345678")).orElseThrow();
                return u;
            });

            Organization org = tm.execute(DatabasePrincipal.ADMIN, em -> {
                Organization o = new Organization(
                        "Org " + suffix,
                        "org." + suffix + "@ticketscenter.vn",
                        "0900000000",
                        "Test description",
                        user
                );
                return orgRepository.save(em, o);
            });

            OrganizationMembership savedMembership = tm.execute(DatabasePrincipal.ADMIN, em -> {
                OrganizationMembership membership = new OrganizationMembership(user, org, OrganizationRole.MANAGER);
                em.persist(membership);
                return membership;
            });

            assertNotNull(savedMembership.getId());

            tm.execute(DatabasePrincipal.ADMIN, em -> {
                // findById
                Optional<OrganizationMembership> found = repository.findById(em, savedMembership.getId());
                assertTrue(found.isPresent());
                assertEquals(OrganizationRole.MANAGER, found.get().getRole());
                assertTrue(found.get().isActive());

                // findByOrgAndUser
                Optional<OrganizationMembership> foundByOrgUser = repository.findByOrgAndUser(em, org.getId(), user.getId());
                assertTrue(foundByOrgUser.isPresent());
                assertEquals(savedMembership.getId(), foundByOrgUser.get().getId());

                // findMembersByOrgId
                List<OrganizationMemberDto> members = repository.findMembersByOrgId(em, org.getId());
                assertFalse(members.isEmpty());
                OrganizationMemberDto memberDto = members.stream()
                        .filter(m -> m.membershipId().equals(savedMembership.getId()))
                        .findFirst()
                        .orElse(null);
                assertNotNull(memberDto);
                assertEquals(user.getId(), memberDto.userId());
                assertEquals(user.getUserName(), memberDto.userName());
                assertEquals(user.getFullName(), memberDto.fullName());
                assertEquals(user.getEmail(), memberDto.email());
                assertEquals(OrganizationRole.MANAGER, memberDto.role());
                assertTrue(memberDto.isActive());

                // findMembershipsByUserId
                List<MembershipSummaryDto> summaries = repository.findMembershipsByUserId(em, user.getId());
                assertFalse(summaries.isEmpty());
                MembershipSummaryDto summaryDto = summaries.stream()
                        .filter(s -> s.organizationId().equals(org.getId()))
                        .findFirst()
                        .orElse(null);
                assertNotNull(summaryDto);
                assertEquals(org.getName(), summaryDto.organizationName());
                assertEquals(OrganizationRole.MANAGER, summaryDto.role());
                assertTrue(summaryDto.isActive());

                // Test null queries return empty / default
                assertEquals(Optional.empty(), repository.findById(em, null));
                assertEquals(Optional.empty(), repository.findByOrgAndUser(em, null, user.getId()));
                assertEquals(Optional.empty(), repository.findByOrgAndUser(em, org.getId(), null));
                assertTrue(repository.findMembersByOrgId(em, null).isEmpty());
                assertTrue(repository.findMembershipsByUserId(em, null).isEmpty());

                return null;
            });
            // Commands go through usp_AddMember / usp_ChangeMemberRole (migration 070).
            UUID managerId = user.getId();
            String staffSuffix = UUID.randomUUID().toString().substring(0, 8);
            User staff = tm.execute(DatabasePrincipal.AUTH, em -> {
                User u = userRepository.findById(em, userRepository.register(em, "staff." + staffSuffix + "@ticketscenter.vn", "stf" + staffSuffix, "Staff " + staffSuffix, "hash12345678")).orElseThrow();
                return u;
            });

            var added = tm.execute(DatabasePrincipal.MANAGER, em ->
                    repository.addMember(em, org.getId(), managerId, staff.getId(), OrganizationRole.CHECK_IN_STAFF));
            assertEquals(OrganizationRole.CHECK_IN_STAFF, added.role());
            assertTrue(added.active());

            var deactivated = tm.execute(DatabasePrincipal.MANAGER, em ->
                    repository.changeMember(em, org.getId(), managerId, staff.getId(), null, Boolean.FALSE));
            assertEquals(OrganizationRole.CHECK_IN_STAFF, deactivated.role(), "a null role keeps the current role");
            assertFalse(deactivated.active());

            var readded = tm.execute(DatabasePrincipal.MANAGER, em ->
                    repository.addMember(em, org.getId(), managerId, staff.getId(), OrganizationRole.MANAGER));
            assertEquals(added.membershipId(), readded.membershipId(), "re-adding reactivates the same row");
            assertEquals(OrganizationRole.MANAGER, readded.role());

            // Two managers now, so demoting one is allowed; demoting the last one is stopped by the trigger.
            tm.execute(DatabasePrincipal.MANAGER, em ->
                    repository.changeMember(em, org.getId(), managerId, staff.getId(), OrganizationRole.CHECK_IN_STAFF, null));
            IllegalStateException lastManager = assertThrows(IllegalStateException.class, () ->
                    tm.execute(DatabasePrincipal.MANAGER, em ->
                            repository.changeMember(em, org.getId(), managerId, managerId, OrganizationRole.CHECK_IN_STAFF, null)));
            assertTrue(lastManager.getMessage().contains("CANNOT_DEACTIVATE_LAST_MANAGER"));
        }
    }
}

