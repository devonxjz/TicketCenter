package vn.ticketscenter.identity.service;

import vn.ticketscenter.identity.dto.CurrentUserDto;
import vn.ticketscenter.identity.dto.ProfileDto;
import java.util.UUID;

/** Reads and updates the signed-in user's own account data. */
public interface ProfileService {
    ProfileDto getProfile(UUID actorId);

    /**
     * Updates the profile through {@code usp_UpdateMyProfile}.
     *
     * @return the stored profile, or {@code null} when there is no active user
     * @throws IllegalArgumentException when the name or phone is invalid
     */
    ProfileDto updateProfile(UUID actorId, String fullName, String phone);

    CurrentUserDto getCurrentUser(UUID actorId);
}
