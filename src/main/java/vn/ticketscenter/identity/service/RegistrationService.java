package vn.ticketscenter.identity.service;

import vn.ticketscenter.identity.dto.RegisterRequestDto;

public interface RegistrationService {
    /**
     * Registers a new CUSTOMER account after normalising the email and checking that the email and user name
     * are unused.
     *
     * @throws IllegalArgumentException when a field is invalid
     * @throws IdentityException when the email or user name is already taken
     */
    boolean register(RegisterRequestDto request);
}
