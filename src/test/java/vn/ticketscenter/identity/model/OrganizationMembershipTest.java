package vn.ticketscenter.identity.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

public class OrganizationMembershipTest {

    @Test
    @DisplayName("Verify constructor and getter methods")
    void testConstructorAndGetters() {
        User user = new User("member@ticketscenter.vn", "member1", "Member One", "hash");
        Organization org = new Organization("Test Org", "test@org.vn", null);
        OrganizationMembership membership = new OrganizationMembership(user, org, OrganizationRole.MANAGER);

        assertEquals(user, membership.getUser());
        assertEquals(org, membership.getOrganization());
        assertEquals(OrganizationRole.MANAGER, membership.getRole());
        assertTrue(membership.isActive());
        assertNotNull(membership.getJoinedAt());
        assertNull(membership.getId());
        assertNull(membership.getVersion());
    }

    @Test
    @DisplayName("Verify changeRole updates role correctly")
    void testChangeRole() {
        User user = new User("member@ticketscenter.vn", "member1", "Member One", "hash");
        Organization org = new Organization("Test Org", "test@org.vn", null);
        OrganizationMembership membership = new OrganizationMembership(user, org, OrganizationRole.MANAGER);

        membership.changeRole(OrganizationRole.CHECK_IN_STAFF);
        assertEquals(OrganizationRole.CHECK_IN_STAFF, membership.getRole());

        membership.changeRole(OrganizationRole.MANAGER);
        assertEquals(OrganizationRole.MANAGER, membership.getRole());
    }

    @Test
    @DisplayName("Verify setActive updates active flag correctly")
    void testSetActive() {
        User user = new User("member@ticketscenter.vn", "member1", "Member One", "hash");
        Organization org = new Organization("Test Org", "test@org.vn", null);
        OrganizationMembership membership = new OrganizationMembership(user, org, OrganizationRole.MANAGER);

        assertTrue(membership.isActive());

        membership.setActive(false);
        assertFalse(membership.isActive());

        membership.setActive(true);
        assertTrue(membership.isActive());
    }

    @Test
    @DisplayName("Verify onCreate pre-persist populates joinedAt if null")
    void testOnCreate() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        OrganizationMembership membership = new OrganizationMembership();
        Method onCreate = OrganizationMembership.class.getDeclaredMethod("onCreate");
        onCreate.setAccessible(true);
        onCreate.invoke(membership);
        assertNotNull(membership.getJoinedAt());
    }
}
