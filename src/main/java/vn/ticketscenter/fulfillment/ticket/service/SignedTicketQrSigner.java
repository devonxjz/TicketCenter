package vn.ticketscenter.fulfillment.ticket.service;

import vn.ticketscenter.config.fulfillment.TicketQrConfig;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.json.JSONObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Signs ticket QR codes and renders them (LIEM-05).
 * - Payload JSON canonical UTF-8: {"id":"<ticket-uuid>","name":"<display-name>"}
 * - HMAC-SHA256 with the application secret key.
 * - Envelope: base64url(payload).base64url(signature)
 * - signatures are compared with MessageDigest.isEqual against timing attacks.
 * - the PNG is drawn with ZXing. The secret, payload and envelope are never logged.
 */
public final class SignedTicketQrSigner {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    public static final int DEFAULT_QR_SIZE = 300;

    private final String secretKey;

    public static SignedTicketQrSigner configured() {
        return new SignedTicketQrSigner(TicketQrConfig.signingSecret());
    }

    public SignedTicketQrSigner(String secretKey) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("Secret key for ticket QR must not be empty");
        }
        this.secretKey = secretKey;
    }

    public record DecodedTicketQr(UUID ticketId, String displayName, String rawJson) {
        public DecodedTicketQr {
            Objects.requireNonNull(ticketId, "ticketId is required");
            Objects.requireNonNull(displayName, "displayName is required");
            Objects.requireNonNull(rawJson, "rawJson is required");
        }
    }

    /**
     * Canonical UTF-8 JSON with a fixed field order: "id" then "name".
     */
    public static String canonicalJsonPayload(UUID ticketId, String displayName) {
        Objects.requireNonNull(ticketId, "ticketId is required");
        String safeName = (displayName != null) ? displayName : "";
        return "{\"id\":\"" + ticketId + "\",\"name\":\"" + escapeJsonString(safeName) + "\"}";
    }

    /**
     * HMAC-SHA256 over the payload's UTF-8 bytes.
     */
    public byte[] signPayload(String payload) {
        Objects.requireNonNull(payload, "payload is required");
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec =
                    new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256 signature", e);
        }
    }

    /**
     * The envelope base64url(payload).base64url(signature).
     */
    public String buildEnvelope(UUID ticketId, String displayName) {
        String payload = canonicalJsonPayload(ticketId, displayName);
        byte[] signature = signPayload(payload);

        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String encodedPayload = encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String encodedSignature = encoder.encodeToString(signature);

        return encodedPayload + "." + encodedSignature;
    }

    /**
     * Verifies and decodes an envelope.
     * Throws SecurityException when the payload or signature was tampered with.
     */
    public DecodedTicketQr verifyAndDecode(String envelope) {
        if (envelope == null || !envelope.contains(".")) {
            throw new SecurityException("Invalid ticket QR envelope format");
        }
        int dotIndex = envelope.indexOf('.');
        if (dotIndex == 0 || dotIndex == envelope.length() - 1) {
            throw new SecurityException("Malformed ticket QR envelope parts");
        }

        String encodedPayload = envelope.substring(0, dotIndex);
        String encodedSignature = envelope.substring(dotIndex + 1);

        Base64.Decoder decoder = Base64.getUrlDecoder();
        byte[] payloadBytes;
        byte[] signatureBytes;
        try {
            payloadBytes = decoder.decode(encodedPayload);
            signatureBytes = decoder.decode(encodedSignature);
        } catch (IllegalArgumentException e) {
            throw new SecurityException("Failed to decode base64url envelope parts", e);
        }

        // constant-time comparison (MessageDigest.isEqual) against timing attacks
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec =
                    new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] expectedSignature = mac.doFinal(payloadBytes);

            if (!MessageDigest.isEqual(expectedSignature, signatureBytes)) {
                throw new SecurityException("Ticket QR signature mismatch or tampered");
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Error verifying HMAC signature", e);
        }

        // Parse canonical JSON
        String json = new String(payloadBytes, StandardCharsets.UTF_8);
        try {
            JSONObject obj = new JSONObject(json);
            if (!obj.has("id") || !obj.has("name")) {
                throw new SecurityException("Ticket QR payload missing required attributes");
            }
            UUID ticketId = UUID.fromString(obj.getString("id"));
            String name = obj.getString("name");
            return new DecodedTicketQr(ticketId, name, json);
        } catch (Exception e) {
            throw new SecurityException("Invalid JSON structure in QR payload", e);
        }
    }

    /**
     * Draws the envelope as a QR PNG with ZXing.
     */
    public static byte[] generateQrPng(String envelope, int width, int height) {
        Objects.requireNonNull(envelope, "envelope is required");
        int targetWidth = (width > 0) ? width : DEFAULT_QR_SIZE;
        int targetHeight = (height > 0) ? height : DEFAULT_QR_SIZE;

        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);

            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix =
                    writer.encode(
                            envelope, BarcodeFormat.QR_CODE, targetWidth, targetHeight, hints);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate QR PNG image", e);
        }
    }

    private static String escapeJsonString(String input) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < ' ') {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        return sb.toString();
    }
}
