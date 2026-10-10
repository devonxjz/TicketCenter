package vn.ticketscenter.settlement.commission.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.settlement.commission.CommissionRuleException;

import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class CommissionRulePolicyTest {
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UserPrincipal ADMIN = new UserPrincipal(ACTOR_ID, PlatformRole.ADMIN, 1);
    private static final UserPrincipal CUSTOMER = new UserPrincipal(ACTOR_ID, PlatformRole.CUSTOMER, 1);

    @Test
    void requiresPlatformAdmin() {
        assertDoesNotThrow(() -> CommissionRulePolicy.requireAdmin(ADMIN));
        assertEquals("ADMIN_REQUIRED", assertThrows(CommissionRuleException.class,
                () -> CommissionRulePolicy.requireAdmin(CUSTOMER)).code());
        assertEquals("AUTHENTICATION_REQUIRED", assertThrows(CommissionRuleException.class,
                () -> CommissionRulePolicy.requireAdmin(null)).code());
    }

    @ParameterizedTest
    @MethodSource("attachmentCounts")
    void allowsOnlyUnattachedRules(long count, String expectedCode) {
        if (expectedCode == null) {
            assertDoesNotThrow(() -> CommissionRulePolicy.requireEditable(ADMIN, count));
        } else {
            assertEquals(expectedCode, assertThrows(CommissionRuleException.class,
                    () -> CommissionRulePolicy.requireEditable(ADMIN, count)).code());
        }
    }

    static Stream<Arguments> attachmentCounts() {
        return Stream.of(
                Arguments.of(0L, null),
                Arguments.of(1L, "RULE_ALREADY_ATTACHED_TO_EVENTS"),
                Arguments.of(5L, "RULE_ALREADY_ATTACHED_TO_EVENTS"),
                Arguments.of(Long.MAX_VALUE, "RULE_ALREADY_ATTACHED_TO_EVENTS"));
    }

    @Test
    void rejectsNegativeBackendCount() {
        assertThrows(IllegalStateException.class, () -> CommissionRulePolicy.requireEditable(ADMIN, -1L));
    }

    @ParameterizedTest
    @MethodSource("unauthorizedStates")
    void checksAuthorizationBeforeState(UserPrincipal actor, long count, String expectedCode) {
        assertEquals(expectedCode, assertThrows(CommissionRuleException.class,
                () -> CommissionRulePolicy.requireEditable(actor, count)).code());
    }

    static Stream<Arguments> unauthorizedStates() {
        return Stream.of(
                Arguments.of(CUSTOMER, -1L, "ADMIN_REQUIRED"),
                Arguments.of(CUSTOMER, 0L, "ADMIN_REQUIRED"),
                Arguments.of(CUSTOMER, 1L, "ADMIN_REQUIRED"),
                Arguments.of(null, -1L, "AUTHENTICATION_REQUIRED"),
                Arguments.of(null, 0L, "AUTHENTICATION_REQUIRED"),
                Arguments.of(null, 1L, "AUTHENTICATION_REQUIRED"));
    }
}
