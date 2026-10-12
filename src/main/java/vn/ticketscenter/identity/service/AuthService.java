package vn.ticketscenter.identity.service;

import jakarta.persistence.PersistenceException;
import redis.clients.jedis.Jedis;
import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.identity.dto.CurrentUserDto;
import vn.ticketscenter.identity.dto.LoginRequestDto;
import vn.ticketscenter.identity.dto.RegisterRequestDto;
import vn.ticketscenter.identity.model.OrganizationRole;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.model.User;
import vn.ticketscenter.identity.model.UserStatus;
import vn.ticketscenter.identity.repository.UserRepository;
import vn.ticketscenter.identity.security.UserPrincipal;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AuthService {

    private static final Logger LOGGER = Logger.getLogger(AuthService.class.getName());
    private static final int REDIS_CACHE_TTL_SECONDS = 180;

    private final TransactionManager transactions;
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final RedisClientProvider redisProvider;

    private static final String DUMMY_HASH = "c2FsdHNhbHRzYWx0c2FsdA==:aGFzaGhhc2hoYXNoaGFzaGhhc2hoYXNoaGFzaGhhc2g=";

    public AuthService(TransactionManager transactions, UserRepository userRepository, PasswordHasher passwordHasher) {
        this(transactions, userRepository, passwordHasher, null);
    }

    public AuthService(TransactionManager transactions, UserRepository userRepository,
                       PasswordHasher passwordHasher, RedisClientProvider redisProvider) {
        this.transactions = Objects.requireNonNull(transactions, "transactions must not be null");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher must not be null");
        this.redisProvider = redisProvider;
    }

    /**
     * Đăng ký tài khoản người dùng mới.
     * Chuẩn hóa email, userName trước khi kiểm tra duy nhất.
     */
    public boolean register(RegisterRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Dữ liệu đăng ký không được để trống");
        }

        String email = AccountService.normalizeEmail(request.email());
        if (email.isBlank() || !email.contains("@") || email.length() > 320) {
            throw new IllegalArgumentException("Email không đúng định dạng");
        }

        String userName = request.userName();
        if (userName == null || userName.isBlank() || userName.length() < 3 || userName.length() > 100) {
            throw new IllegalArgumentException("Tên đăng nhập phải từ 3 đến 100 ký tự");
        }

        String fullName = request.fullName();
        if (fullName == null || fullName.isBlank() || fullName.length() > 200) {
            throw new IllegalArgumentException("Họ và tên không được để trống hoặc vượt quá 200 ký tự");
        }

        String password = request.password();
        if (password == null || password.length() < 12 || password.length() > 256) {
            throw new IllegalArgumentException("Mật khẩu phải có độ dài từ 12 đến 256 ký tự");
        }

        String passwordHash = passwordHasher.hash(password);

        try {
            return Boolean.TRUE.equals(transactions.execute(DatabasePrincipal.AUTH, em -> {
                if (userRepository.existsByNormalizedEmail(em, email)) {
                    throw new IllegalStateException("EMAIL_ALREADY_EXISTS: Email đã được sử dụng");
                }
                if (userRepository.existsByNormalizedUserName(em, userName)) {
                    throw new IllegalStateException("USERNAME_ALREADY_EXISTS: Tên đăng nhập đã được sử dụng");
                }

                User newUser = new User(email, userName.trim(), fullName.trim(), passwordHash);
                userRepository.save(em, newUser);
                return true;
            }));
        } catch (PersistenceException ex) {
            LOGGER.log(Level.WARNING, "Lỗi xung đột khi lưu tài khoản: " + ex.getMessage());
            throw new IllegalStateException("IDENTITY_CONFLICT: Email hoặc tên đăng nhập đã tồn tại trong hệ thống");
        }
    }

    /**
     * Xác thực thông tin đăng nhập bằng email (hoặc alias admin) và mật khẩu.
     * Không phân biệt email tồn tại hay sai mật khẩu để chống dò tài khoản.
     */
    public UserPrincipal login(LoginRequestDto request) {
        if (request == null || request.identifier() == null || request.identifier().isBlank()
                || request.password() == null || request.password().isBlank()) {
            throw new SecurityException("INVALID_CREDENTIALS");
        }

        String identifier = request.identifier().trim();
        String normalizedEmail = AccountService.normalizeEmail(identifier);

        User user = transactions.execute(DatabasePrincipal.AUTH, em -> {
            // Tra cứu theo email chuẩn hóa trước, sau đó tra cứu theo username
            User found = userRepository.findByNormalizedEmail(em, normalizedEmail).orElse(null);
            if (found == null) {
                found = userRepository.findByNormalizedUserName(em, identifier).orElse(null);
            }
            if (found == null && "admin".equalsIgnoreCase(identifier)) {
                String adminEmail = AccountService.normalizeEmail(AppConfig.get("admin.email", "admin@fixture.invalid"));
                found = userRepository.findByNormalizedEmail(em, adminEmail).orElse(null);
                if (found == null) {
                    found = em.createQuery("SELECT u FROM User u WHERE u.platformRole = :role ORDER BY CASE WHEN LOWER(u.email) LIKE '%admin%' THEN 0 ELSE 1 END", User.class)
                            .setParameter("role", PlatformRole.ADMIN)
                            .setMaxResults(1)
                            .getResultList().stream().findFirst().orElse(null);
                }
            }
            return found;
        });

        if (user == null) {
            passwordHasher.verify(request.password(), DUMMY_HASH);
            throw new SecurityException("INVALID_CREDENTIALS");
        }

        if (!passwordHasher.verify(request.password(), user.getPasswordHash())) {
            throw new SecurityException("INVALID_CREDENTIALS");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new SecurityException("ACCOUNT_DISABLED");
        }

        OrganizationRole orgRole = transactions.execute(DatabasePrincipal.AUTH, em -> {
            try {
                return em.createQuery(
                        "SELECT m.role FROM OrganizationMembership m WHERE m.user.id = :uid AND m.active = true ORDER BY CASE WHEN m.role = :mgr THEN 1 ELSE 2 END",
                        OrganizationRole.class)
                        .setParameter("uid", user.getId())
                        .setParameter("mgr", OrganizationRole.MANAGER)
                        .setMaxResults(1)
                        .getResultList().stream().findFirst().orElse(null);
            } catch (Exception e) {
                return null;
            }
        });

        return new UserPrincipal(user.getId(), user.getPlatformRole(), user.getAuthVersion(), orgRole, user.getEmail());
    }

    /**
     * Xác thực tính hợp lệ của phiên làm việc (User còn ACTIVE và authVersion chưa thay đổi).
     * Áp dụng Cache-Aside qua Redis (nếu có), tự động fallback SQL Server nếu Redis gặp sự cố.
     */
    public boolean isSessionValid(UUID actorId, int expectedAuthVersion) {
        if (actorId == null) return false;

        String cacheKey = "auth:user:" + actorId;

        // 1. Đọc từ Redis Cache-Aside
        if (redisProvider != null) {
            try (Jedis jedis = redisProvider.getResource()) {
                String cached = jedis.get(cacheKey);
                if (cached != null) {
                    String[] parts = cached.split(":");
                    if (parts.length == 2) {
                        String statusStr = parts[0];
                        int authVersion = Integer.parseInt(parts[1]);
                        return "ACTIVE".equalsIgnoreCase(statusStr) && authVersion == expectedAuthVersion;
                    }
                }
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Không thể đọc Redis cache cho user session, fallback DB: " + ex.getMessage());
            }
        }

        // 2. Cache miss hoặc Redis lỗi: Đọc trực tiếp SQL Server
        User user = transactions.execute(DatabasePrincipal.AUTH, em ->
                userRepository.findById(em, actorId).orElse(null));

        if (user == null || user.getStatus() != UserStatus.ACTIVE || user.getAuthVersion() != expectedAuthVersion) {
            // Invalidate cache nếu có
            if (redisProvider != null) {
                try (Jedis jedis = redisProvider.getResource()) {
                    jedis.del(cacheKey);
                } catch (Exception ignored) {}
            }
            return false;
        }

        // 3. Nạp lại cache (chỉ lưu status và authVersion, tuyệt đối không lưu password/secret)
        if (redisProvider != null) {
            try (Jedis jedis = redisProvider.getResource()) {
                jedis.setex(cacheKey, REDIS_CACHE_TTL_SECONDS, user.getStatus().name() + ":" + user.getAuthVersion());
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Không thể ghi Redis cache cho user session: " + ex.getMessage());
            }
        }

        return true;
    }

    /**
     * Lấy thông tin tài khoản hiện tại cho endpoint GET /api/me.
     */
    public CurrentUserDto getCurrentUser(UUID actorId) {
        if (actorId == null) return null;

        return transactions.execute(DatabasePrincipal.AUTH, em -> {
            User user = userRepository.findById(em, actorId).orElse(null);
            if (user == null) return null;
            return new CurrentUserDto(
                    user.getId(),
                    user.getEmail(),
                    user.getUserName(),
                    user.getFullName(),
                    user.getPlatformRole(),
                    user.hasPlatformRole("ADMIN")
            );
        });
    }

    /**
     * Vô hiệu hóa cache Redis của User khi có biến động thông tin xác thực.
     */
    public void evictUserCache(UUID actorId) {
        if (actorId != null && redisProvider != null) {
            try (Jedis jedis = redisProvider.getResource()) {
                jedis.del("auth:user:" + actorId);
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Lỗi khi xóa cache user: " + ex.getMessage());
            }
        }
    }

    /**
     * Thu hồi toàn bộ phiên làm việc của người dùng bằng cách tăng authVersion.
     */
    public void revokeAllSessions(UUID actorId) {
        if (actorId == null) return;
        transactions.execute(DatabasePrincipal.AUTH, em -> {
            User user = userRepository.findById(em, actorId).orElse(null);
            if (user != null) {
                int nextVersion = user.getAuthVersion() + 1;
                userRepository.updateAuthVersion(em, actorId, nextVersion);
                evictUserCache(actorId);
            }
            return null;
        });
    }
}
