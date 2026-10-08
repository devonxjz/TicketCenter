package vn.ticketscenter.config;

/**
 * Cấu hình tích hợp Cổng thanh toán VNPAY Sandbox.
 */
public final class VnPayConfig {

    private VnPayConfig() {
        // Utility class không cho phép khởi tạo instance
    }

    public static String getTmnCode() {
        return AppConfig.get("vnpay.tmn.code", "");
    }

    public static String getHashSecret() {
        return AppConfig.get("vnpay.hash.secret", "");
    }

    public static String getPayUrl() {
        return AppConfig.get("vnpay.pay.url", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
    }

    public static String getReturnUrl() {
        return AppConfig.get("vnpay.return.url", "http://localhost:8080/ticketscenter/api/payments/vnpay/return");
    }

    public static String getApiUrl() {
        return AppConfig.get("vnpay.api.url", "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction");
    }
}
