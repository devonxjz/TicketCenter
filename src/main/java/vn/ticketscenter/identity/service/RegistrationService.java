package vn.ticketscenter.identity.service;

import jakarta.persistence.PersistenceException;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.dto.RegisterRequestDto;
import vn.ticketscenter.identity.error.IdentityError;
import vn.ticketscenter.identity.error.IdentityException;
import vn.ticketscenter.identity.model.User;
import vn.ticketscenter.identity.dao.UserRepository;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RegistrationService {

    private static final Logger LOGGER = Logger.getLogger(RegistrationService.class.getName());

    private final TransactionManager transactions;
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public RegistrationService(
            TransactionManager transactions,
            UserRepository userRepository,
            PasswordHasher passwordHasher) {
        this.transactions = Objects.requireNonNull(transactions, "transactions must not be null");
        this.userRepository =
                Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.passwordHasher =
                Objects.requireNonNull(passwordHasher, "passwordHasher must not be null");
    }

    /**
     * Registers a new CUSTOMER account after normalising the email and checking that the email and user name
     * are unused.
     *
     * @throws IllegalArgumentException when a field is invalid
     * @throws IdentityException when the email or user name is already taken
     */
    public boolean register(RegisterRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Dữ liệu đăng ký không được để trống");
        }
        String email = EmailNormalizer.normalize(request.email());
        IdentityValidator.requireEmail(email);
        IdentityValidator.requireUserName(request.userName());
        IdentityValidator.requireFullName(request.fullName());
        IdentityValidator.requirePassword(request.password());

        String userName = request.userName().trim();
        String fullName = request.fullName().trim();
        String passwordHash = passwordHasher.hash(request.password());

        try {
            return Boolean.TRUE.equals(
                    transactions.execute(
                            DatabasePrincipal.AUTH,
                            em -> {
                                if (userRepository.existsByNormalizedEmail(em, email)) {
                                    throw new IdentityException(
                                            IdentityError.EMAIL_ALREADY_EXISTS,
                                            "EMAIL_ALREADY_EXISTS: Email đã được sử dụng");
                                }
                                if (userRepository.existsByNormalizedUserName(em, userName)) {
                                    throw new IdentityException(
                                            IdentityError.USERNAME_ALREADY_EXISTS,
                                            "USERNAME_ALREADY_EXISTS: Tên đăng nhập đã được sử dụng");
                                }
                                userRepository.save(
                                        em, new User(email, userName, fullName, passwordHash));
                                return true;
                            }));
        } catch (PersistenceException conflict) {
            LOGGER.log(Level.WARNING, "Lỗi xung đột khi lưu tài khoản: " + conflict.getMessage());
            throw new IdentityException(
                    IdentityError.IDENTITY_CONFLICT,
                    "IDENTITY_CONFLICT: Email hoặc tên đăng nhập đã tồn tại trong hệ thống",
                    conflict);
        }
    }
}
