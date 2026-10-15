package vn.ticketscenter.identity.service;

/**
 * Field rules shared by registration and OTP delivery. Each method throws {@link IllegalArgumentException}
 * with the message shown to the client.
 */
public final class IdentityValidator {

    private static final int MAX_EMAIL_LENGTH = 320;

    private IdentityValidator() {
    }

    /** @param normalizedEmail the result of {@link EmailNormalizer#normalize(String)} */
    public static void requireEmail(String normalizedEmail) {
        if (normalizedEmail.isBlank() || !normalizedEmail.contains("@") || normalizedEmail.length() > MAX_EMAIL_LENGTH) {
            throw new IllegalArgumentException("Email không đúng định dạng");
        }
    }

    public static void requireUserName(String userName) {
        if (userName == null || userName.isBlank() || userName.length() < 3 || userName.length() > 100) {
            throw new IllegalArgumentException("Tên đăng nhập phải từ 3 đến 100 ký tự");
        }
    }

    public static void requireFullName(String fullName) {
        if (fullName == null || fullName.isBlank() || fullName.length() > 200) {
            throw new IllegalArgumentException("Họ và tên không được để trống hoặc vượt quá 200 ký tự");
        }
    }

    public static void requirePassword(String password) {
        if (password == null || password.length() < 12 || password.length() > 256) {
            throw new IllegalArgumentException("Mật khẩu phải có độ dài từ 12 đến 256 ký tự");
        }
    }
}
