package vn.ticketscenter.config.error;

/** Dịch vụ bên ngoài (payOS, SMTP, Redis, lưu trữ ảnh...) lỗi hoặc không phản hồi: hiển thị là 502. */
public class UpstreamException extends AppException {

    public UpstreamException(String code, String message) {
        super(502, code, message);
    }

    public UpstreamException(String code, String message, Throwable cause) {
        super(502, code, message, cause);
    }
}
