package vn.ticketscenter.order.dto;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class OrderRequests {
    public static final int MAX_BODY_BYTES = 16 * 1024;

    private OrderRequests() {}

    public static final class BodyTooLargeException extends IllegalArgumentException {
        public BodyTooLargeException() {
            super("Order body exceeds 16384 bytes");
        }
    }

    public static CreateOrderRequest create(InputStream body) throws IOException {
        String value = member(body, "holdId");
        if (value == null
                || !value.matches(
                        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw invalid("holdId must be a UUID");
        }
        return new CreateOrderRequest(UUID.fromString(value));
    }

    public static CouponMutation coupon(UUID orderId, InputStream body) throws IOException {
        String value = member(body, "couponCode");
        return value == null
                ? new CouponMutation.RemoveCoupon(orderId)
                : new CouponMutation.SetCoupon(orderId, value);
    }

    /** SHA-256 of ASCII TC-ORDER, version byte 1, operation byte 1, then actor and Hold UUIDs in big-endian order. */
    public static String fingerprint(UUID actorId, CreateOrderRequest request) {
        Objects.requireNonNull(request, "request");
        return fingerprint(actorId, 1, request.holdId(), null);
    }

    /** Uses operation 2 for set and 3 for remove. Set appends a big-endian UTF-8 byte count and exact code. */
    public static String fingerprint(UUID actorId, CouponMutation mutation) {
        Objects.requireNonNull(mutation, "mutation");
        return switch (mutation) {
            case CouponMutation.SetCoupon set ->
                    fingerprint(actorId, 2, set.orderId(), set.couponCode());
            case CouponMutation.RemoveCoupon remove ->
                    fingerprint(actorId, 3, remove.orderId(), null);
        };
    }

    private static String fingerprint(UUID actorId, int operation, UUID targetId, String code) {
        requireId(actorId);
        try {
            var bytes = new ByteArrayOutputStream();
            var data = new DataOutputStream(bytes);
            data.write("TC-ORDER".getBytes(StandardCharsets.US_ASCII));
            data.writeByte(1);
            data.writeByte(operation);
            data.writeLong(actorId.getMostSignificantBits());
            data.writeLong(actorId.getLeastSignificantBits());
            data.writeLong(targetId.getMostSignificantBits());
            data.writeLong(targetId.getLeastSignificantBits());
            if (code != null) {
                byte[] encoded = code.getBytes(StandardCharsets.UTF_8);
                data.writeInt(encoded.length);
                data.write(encoded);
            }
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cannot fingerprint Order request", e);
        }
    }

    static void requireId(UUID id) {
        if (id == null || (id.getMostSignificantBits() == 0 && id.getLeastSignificantBits() == 0)) {
            throw invalid("Identity must be a nonzero UUID");
        }
    }

    static void requireText(String value, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw invalid("Text is missing or too long");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c)) {
                throw invalid("Control character in text");
            }
            if (Character.isHighSurrogate(c)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) {
                    throw invalid("Unpaired surrogate");
                }
            } else if (Character.isLowSurrogate(c)) {
                throw invalid("Unpaired surrogate");
            }
        }
    }

    private static String member(InputStream body, String name) throws IOException {
        if (body == null) {
            throw invalid("Missing body");
        }
        byte[] bytes = body.readNBytes(MAX_BODY_BYTES + 1);
        if (bytes.length > MAX_BODY_BYTES) {
            throw new BodyTooLargeException();
        }
        String json;
        try {
            json =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes))
                            .toString();
        } catch (CharacterCodingException e) {
            throw invalid("Malformed UTF-8");
        }
        Map<String, String> fields = new Parser(json).object();
        if (fields.size() != 1 || !fields.containsKey(name)) {
            throw invalid("Expected only " + name);
        }
        return fields.get(name);
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private static final class Parser {
        private final String json;
        private int position;

        private Parser(String json) {
            this.json = json;
        }

        private Map<String, String> object() {
            Map<String, String> fields = new HashMap<>();
            expect('{');
            if (!take('}')) {
                do {
                    String name = string();
                    if (fields.containsKey(name)) {
                        throw invalid("Duplicate member");
                    }
                    expect(':');
                    whitespace();
                    String value;
                    if (json.startsWith("null", position)) {
                        position += 4;
                        value = null;
                    } else {
                        value = string();
                    }
                    fields.put(name, value);
                } while (take(','));
                expect('}');
            }
            whitespace();
            if (position != json.length()) {
                throw invalid("Trailing JSON content");
            }
            return fields;
        }

        private String string() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (position < json.length()) {
                char c = json.charAt(position++);
                if (c == '"') {
                    String result = value.toString();
                    validateUnicode(result);
                    return result;
                }
                if (c < 0x20) {
                    throw invalid("Unescaped control character");
                }
                if (c == '\\') {
                    if (position == json.length()) {
                        throw invalid("Incomplete escape");
                    }
                    char escaped = json.charAt(position++);
                    c =
                            switch (escaped) {
                                case '"', '\\', '/' -> escaped;
                                case 'b' -> '\b';
                                case 'f' -> '\f';
                                case 'n' -> '\n';
                                case 'r' -> '\r';
                                case 't' -> '\t';
                                case 'u' -> unicodeEscape();
                                default -> throw invalid("Unsupported escape");
                            };
                }
                value.append(c);
            }
            throw invalid("Unterminated string");
        }

        private char unicodeEscape() {
            if (position + 4 > json.length()) {
                throw invalid("Incomplete Unicode escape");
            }
            int value = 0;
            for (int i = 0; i < 4; i++) {
                char c = json.charAt(position++);
                int digit =
                        c >= '0' && c <= '9'
                                ? c - '0'
                                : c >= 'a' && c <= 'f'
                                        ? c - 'a' + 10
                                        : c >= 'A' && c <= 'F' ? c - 'A' + 10 : -1;
                if (digit < 0) {
                    throw invalid("Invalid Unicode escape");
                }
                value = value * 16 + digit;
            }
            return (char) value;
        }

        private void validateUnicode(String value) {
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (Character.isHighSurrogate(c)) {
                    if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) {
                        throw invalid("Unpaired surrogate");
                    }
                } else if (Character.isLowSurrogate(c)) {
                    throw invalid("Unpaired surrogate");
                }
            }
        }

        private boolean take(char c) {
            whitespace();
            if (position < json.length() && json.charAt(position) == c) {
                position++;
                return true;
            }
            return false;
        }

        private void expect(char c) {
            if (!take(c)) {
                throw invalid("Invalid JSON object");
            }
        }

        private void whitespace() {
            while (position < json.length()) {
                char c = json.charAt(position);
                if (c != ' ' && c != '\t' && c != '\r' && c != '\n') {
                    break;
                }
                position++;
            }
        }
    }
}
