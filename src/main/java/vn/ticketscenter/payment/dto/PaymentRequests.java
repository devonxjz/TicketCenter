package vn.ticketscenter.payment.dto;

import org.json.JSONException;
import org.json.JSONObject;
import vn.ticketscenter.payment.service.PaymentException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class PaymentRequests {
    private static final int MAX_BODY_BYTES = 16 * 1024;

    private PaymentRequests() { }

    public static InitiatePaymentRequest parse(InputStream input) throws IOException {
        if (input == null) {
            return InitiatePaymentRequest.empty();
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[1024];
        int read;
        int total = 0;
        while ((read = input.read(chunk)) != -1) {
            total += read;
            if (total > MAX_BODY_BYTES) {
                throw new PaymentException(413, "PAYLOAD_TOO_LARGE", "Payment request body is too large.");
            }
            buffer.write(chunk, 0, read);
        }
        String text = buffer.toString(StandardCharsets.UTF_8).trim();
        if (text.isEmpty()) {
            return InitiatePaymentRequest.empty();
        }
        try {
            JSONObject json = new JSONObject(text);
            String bankCode = null;
            if (json.has("bankCode") && !json.isNull("bankCode")) {
                bankCode = json.getString("bankCode");
            }
            return InitiatePaymentRequest.of(bankCode);
        } catch (JSONException | IllegalArgumentException failure) {
            throw new PaymentException(400, "INVALID_REQUEST", "Invalid payment request payload: " + failure.getMessage());
        }
    }
}
