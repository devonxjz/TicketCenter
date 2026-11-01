package vn.ticketscenter.fulfillment.checkin.security;

import java.util.Map;
import vn.ticketscenter.config.fulfillment.TicketQrConfig;
import vn.ticketscenter.config.web.JsonObjectParser;
import vn.ticketscenter.fulfillment.checkin.CheckInException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Verifies bounded bytes before parsing untrusted JSON. */
public final class SignedQrDecoder {
    public record Decoded(UUID ticketId, String name) {}

    private static final String STRING =
            "\"(?:[^\"\\\\\\x00-\\x1f]|\\\\(?:[\"\\\\/bfnrt]|u[0-9a-fA-F]{4}))*\"";
    private static final Pattern OBJECT =
            Pattern.compile(
                    "\\s*\\{\\s*"
                            + STRING
                            + "\\s*:\\s*"
                            + STRING
                            + "\\s*,\\s*"
                            + STRING
                            + "\\s*:\\s*"
                            + STRING
                            + "\\s*}\\s*");
    private final byte[] secret;

    public SignedQrDecoder(byte[] secret) {
        if (secret == null || secret.length < 32) {
            throw new IllegalArgumentException(
                    "Ticket signing secret needs at least 32 UTF-8 bytes");
        }
        this.secret = secret.clone();
    }

    public static SignedQrDecoder configured() {
        try {
            return new SignedQrDecoder(
                    TicketQrConfig.signingSecret().getBytes(StandardCharsets.UTF_8));
        } catch (IllegalStateException failure) {
            throw new CheckInException(
                    503, "QR_SIGNING_UNAVAILABLE", "Hệ thống xác thực mã QR chưa được cấu hình.");
        }
    }

    public Decoded decode(String envelope) {
        try {
            if (envelope == null
                    || envelope.length() > 4096
                    || !envelope.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]{43}")) {
                throw invalid();
            }
            int dot = envelope.indexOf('.');
            String payload = envelope.substring(0, dot), signature = envelope.substring(dot + 1);
            byte[] bytes = Base64.getUrlDecoder().decode(payload);
            byte[] received = Base64.getUrlDecoder().decode(signature);
            Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
            if (bytes.length > 3000
                    || !base64.encodeToString(bytes).equals(payload)
                    || !base64.encodeToString(received).equals(signature)) {
                throw invalid();
            }

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            if (!MessageDigest.isEqual(mac.doFinal(bytes), received)) {
                throw invalid();
            }

            String json =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes))
                            .toString();
            if (!OBJECT.matcher(json).matches()) {
                throw invalid();
            }
            Map<String, String> values = JsonObjectParser.parse(new StringReader(json));
            if (!values.keySet().equals(Set.of("id", "name"))) {
                throw invalid();
            }

            String id = values.get("id"), name = values.get("name");
            if (!id.matches("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
                    || name.isBlank()
                    || name.length() > 373) {
                throw invalid();
            }
            return new Decoded(UUID.fromString(id), name);
        } catch (CheckInException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid();
        }
    }

    private static CheckInException invalid() {
        return new CheckInException(
                400, "INVALID_SIGNATURE", "Mã QR không hợp lệ hoặc đã bị chỉnh sửa.");
    }
}
