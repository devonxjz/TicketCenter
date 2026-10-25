package vn.ticketscenter.identity.service;

import vn.ticketscenter.identity.dto.LoginRequestDto;
import vn.ticketscenter.identity.security.UserPrincipal;

/**
 * Authenticates a user by email, user name or the {@code admin} alias. An unknown account and a wrong password
 * are indistinguishable to the caller, in message and in time.
 */
public interface LoginService {
    /** @throws IdentityException {@code INVALID_CREDENTIALS} or {@code ACCOUNT_DISABLED} */
    UserPrincipal login(LoginRequestDto request);
}
