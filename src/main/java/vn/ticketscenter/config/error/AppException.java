package vn.ticketscenter.config.error;

/**
 * Gốc của mọi lỗi nghiệp vụ/hạ tầng của ứng dụng. Mang theo mã HTTP và mã lỗi ổn định để
 * {@code ErrorPageServlet} (và các servlet API) dựng phản hồi thống nhất mà không đoán từ loại exception.
 */
public class AppException extends RuntimeException {

    private final int status;
    private final String code;

    public AppException(int status, String code, String message) {
        this(status, code, message, null);
    }

    public AppException(int status, String code, String message, Throwable cause) {
        super(message, cause);
        if (status < 400 || status > 599) {
            throw new IllegalArgumentException("invalid HTTP status: " + status);
        }
        this.status = status;
        this.code = code == null || code.isBlank() ? "REQUEST_FAILED" : code;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }
}
