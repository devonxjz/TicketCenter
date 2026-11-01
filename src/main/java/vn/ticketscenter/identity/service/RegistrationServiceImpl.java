package vn.ticketscenter.identity.service;

import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.dto.RegisterRequestDto;
import vn.ticketscenter.identity.dao.UserRepository;

import java.util.Objects;

public class RegistrationServiceImpl implements RegistrationService {

    private final TransactionManager transactions;
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public RegistrationServiceImpl(
            TransactionManager transactions,
            UserRepository userRepository,
            PasswordHasher passwordHasher) {
        this.transactions = Objects.requireNonNull(transactions, "transactions must not be null");
        this.userRepository =
                Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.passwordHasher =
                Objects.requireNonNull(passwordHasher, "passwordHasher must not be null");
    }

    @Override
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

        transactions.execute(
                DatabasePrincipal.AUTH,
                em -> userRepository.register(em, email, userName, fullName, passwordHash));
        return true;
    }
}
