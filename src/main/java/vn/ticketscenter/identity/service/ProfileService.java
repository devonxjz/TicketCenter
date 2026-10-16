package vn.ticketscenter.identity.service;

import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.dto.CurrentUserDto;
import vn.ticketscenter.identity.dto.ProfileDto;
import vn.ticketscenter.identity.model.User;
import vn.ticketscenter.identity.dao.UserRepository;

import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/** Reads and updates the signed-in user's own account data. */
public class ProfileService {

    /** {@code usp_UpdateMyProfile} THROW numbers. */
    private static final int PROFILE_INVALID = 52800;
    private static final int PROFILE_USER_MISSING = 52801;

    private final TransactionManager transactions;
    private final UserRepository userRepository;

    public ProfileService(TransactionManager transactions, UserRepository userRepository) {
        this.transactions = Objects.requireNonNull(transactions, "transactions must not be null");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
    }

    public ProfileDto getProfile(UUID actorId) {
        if (actorId == null) {
            return null;
        }
        return transactions.execute(DatabasePrincipal.AUTH, em ->
                userRepository.findById(em, actorId).map(ProfileService::toProfile).orElse(null));
    }

    /**
     * Updates the profile through {@code usp_UpdateMyProfile}.
     *
     * @return the stored profile, or {@code null} when there is no active user
     * @throws IllegalArgumentException when the name or phone is invalid
     */
    public ProfileDto updateProfile(UUID actorId, String fullName, String phone) {
        if (actorId == null) {
            return null;
        }
        if (fullName == null || fullName.isBlank() || fullName.length() > 200) {
            throw new IllegalArgumentException("Họ tên là bắt buộc và tối đa 200 ký tự");
        }
        if (phone != null && phone.length() > 30) {
            throw new IllegalArgumentException("Số điện thoại không hợp lệ");
        }
        try {
            return transactions.execute(DatabasePrincipal.AUTH, em ->
                    userRepository.updateProfile(em, actorId, fullName, phone).orElse(null));
        } catch (RuntimeException failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException sql) {
                    if (sql.getErrorCode() == PROFILE_INVALID) {
                        throw new IllegalArgumentException("Họ tên hoặc số điện thoại không hợp lệ");
                    }
                    if (sql.getErrorCode() == PROFILE_USER_MISSING) {
                        return null;
                    }
                }
            }
            throw failure;
        }
    }

    public CurrentUserDto getCurrentUser(UUID actorId) {
        if (actorId == null) {
            return null;
        }
        return transactions.execute(DatabasePrincipal.AUTH, em -> userRepository.findById(em, actorId)
                .map(user -> new CurrentUserDto(user.getId(), user.getEmail(), user.getUserName(),
                        user.getFullName(), user.getPlatformRole(), user.hasPlatformRole("ADMIN")))
                .orElse(null));
    }

    private static ProfileDto toProfile(User user) {
        return new ProfileDto(user.getId(), user.getEmail(), user.getUserName(), user.getFullName(), user.getPhone());
    }
}
