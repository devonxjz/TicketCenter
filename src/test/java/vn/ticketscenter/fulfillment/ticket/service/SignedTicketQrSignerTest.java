package vn.ticketscenter.fulfillment.ticket.service;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SignedTicketQrSignerTest {
    private static final String SECRET_KEY = "test-secret-key-123456789";
    private SignedTicketQrSigner signer;

    @BeforeEach
    void setUp() {
        signer = new SignedTicketQrSigner(SECRET_KEY);
    }

    @Test
    void rejectsEmptySecretKey() {
        assertThrows(IllegalArgumentException.class, () -> new SignedTicketQrSigner(null));
        assertThrows(IllegalArgumentException.class, () -> new SignedTicketQrSigner(""));
        assertThrows(IllegalArgumentException.class, () -> new SignedTicketQrSigner("   "));
    }

    @Test
    void canonicalJsonPayloadMaintainsStrictOrderAndEscaping() {
        UUID id = UUID.fromString("11111111-2222-3333-4444-555555555555");
        String name = "Rock Concert \"VIP\" & More\nLine2";
        String payload = SignedTicketQrSigner.canonicalJsonPayload(id, name);

        assertTrue(payload.startsWith("{\"id\":\"11111111-2222-3333-4444-555555555555\",\"name\":"));
        assertTrue(payload.contains("\\\"VIP\\\""));
        assertTrue(payload.contains("\\nLine2"));
        assertTrue(payload.endsWith("\"}"));
    }

    @Test
    void buildsAndVerifiesEnvelopeSuccessfully() {
        UUID id = UUID.randomUUID();
        String name = "Hội Nhạc Mùa Thu - Hàng A-12";

        String envelope = signer.buildEnvelope(id, name);
        assertNotNull(envelope);
        assertTrue(envelope.contains("."));

        var decoded = signer.verifyAndDecode(envelope);
        assertEquals(id, decoded.ticketId());
        assertEquals(name, decoded.displayName());
    }

    @Test
    void rejectsTamperedPayloadInEnvelope() {
        UUID id = UUID.randomUUID();
        String name = "Vé Standard";
        String envelope = signer.buildEnvelope(id, name);

        String[] parts = envelope.split("\\.", 2);
        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);

        // Can thiệp ID vé
        String tamperedJson = payloadJson.replace(id.toString(), UUID.randomUUID().toString());
        String tamperedEncodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(tamperedJson.getBytes(StandardCharsets.UTF_8));
        String tamperedEnvelope = tamperedEncodedPayload + "." + parts[1];

        assertThrows(SecurityException.class, () -> signer.verifyAndDecode(tamperedEnvelope));
    }

    @Test
    void rejectsTamperedSignature() {
        UUID id = UUID.randomUUID();
        String envelope = signer.buildEnvelope(id, "Vé VIP");
        String[] parts = envelope.split("\\.", 2);

        // Can thiệp chữ ký
        String tamperedSignature = parts[1] + "TAMPER";
        String tamperedEnvelope = parts[0] + "." + tamperedSignature;

        assertThrows(SecurityException.class, () -> signer.verifyAndDecode(tamperedEnvelope));
    }

    @Test
    void rejectsEnvelopeSignedWithDifferentSecret() {
        UUID id = UUID.randomUUID();
        SignedTicketQrSigner otherSigner = new SignedTicketQrSigner("another-different-secret-key");
        String envelopeFromOther = otherSigner.buildEnvelope(id, "Vé VIP");

        assertThrows(SecurityException.class, () -> signer.verifyAndDecode(envelopeFromOther));
    }

    @Test
    void rejectsMalformedEnvelopeInputs() {
        assertThrows(SecurityException.class, () -> signer.verifyAndDecode(null));
        assertThrows(SecurityException.class, () -> signer.verifyAndDecode("no-dot-envelope"));
        assertThrows(SecurityException.class, () -> signer.verifyAndDecode(".starts-with-dot"));
        assertThrows(SecurityException.class, () -> signer.verifyAndDecode("ends-with-dot."));
    }

    @Test
    void generatesValidQrPngAndDecodesBackWithZXing() throws Exception {
        UUID id = UUID.randomUUID();
        String name = "Live Show 2026";
        String envelope = signer.buildEnvelope(id, name);

        byte[] pngBytes = SignedTicketQrSigner.generateQrPng(envelope, 300, 300);
        assertNotNull(pngBytes);
        assertTrue(pngBytes.length > 0);

        // Dùng ZXing MultiFormatReader quét lại từ ảnh PNG
        var image = ImageIO.read(new ByteArrayInputStream(pngBytes));
        assertNotNull(image);

        var source = new BufferedImageLuminanceSource(image);
        var bitmap = new BinaryBitmap(new HybridBinarizer(source));
        Result result = new MultiFormatReader().decode(bitmap);

        assertEquals(envelope, result.getText());

        // Giải mã nội dung text thu được từ ảnh QR
        var decoded = signer.verifyAndDecode(result.getText());
        assertEquals(id, decoded.ticketId());
        assertEquals(name, decoded.displayName());
    }
}
