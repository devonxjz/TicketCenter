package vn.ticketscenter.identity.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginRedirectTest {
    @Test
    void acceptsPathsInsideTheApplication() {
        assertTrue(LoginServlet.isLocalRedirect("/ticketscenter/me/orders?page=2", "/ticketscenter"));
        assertTrue(LoginServlet.isLocalRedirect("/me/orders", ""));
    }

    @Test
    void rejectsTargetsThatLeaveTheApplication() {
        for (String target : new String[]{null, "", "https://evil.example", "//evil.example", "/\\evil.example",
                "javascript:alert(1)", "/other-app/page", "/ticketscenter-evil", "/ticketscenter/a\r\nSet-Cookie: x=1"}) {
            assertFalse(LoginServlet.isLocalRedirect(target, "/ticketscenter"), String.valueOf(target));
        }
    }
}
