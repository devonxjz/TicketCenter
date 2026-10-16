package vn.ticketscenter.identity.service;

import jakarta.persistence.EntityManager;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.model.User;
import vn.ticketscenter.identity.dao.UserRepository;

import java.util.Objects;
import java.util.UUID;

/** Confirms, inside a transaction, that an acting user really holds the ADMIN platform role. */
public class AdminGuard {

    private final UserRepository userRepository;

    public AdminGuard(UserRepository userRepository) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null.");
    }

    /**
     * @param action what the admin is trying to do, completing "Chỉ quản trị viên mới có quyền …"
     * @throws IllegalArgumentException when the user does not exist
     * @throws SecurityException when the user is not an admin
     */
    public void requireAdmin(EntityManager em, UUID userId, String action) {
        User user = userRepository.findById(em, userId)
                .orElseThrow(() -> new IllegalArgumentException("USER_NOT_FOUND: Người dùng không tồn tại"));
        if (user.getPlatformRole() != PlatformRole.ADMIN) {
            throw new SecurityException("FORBIDDEN: Chỉ quản trị viên mới có quyền " + action);
        }
    }
}
