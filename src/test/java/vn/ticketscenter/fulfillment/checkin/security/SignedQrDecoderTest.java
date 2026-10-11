package vn.ticketscenter.fulfillment.checkin.security;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.fulfillment.checkin.CheckInException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SignedQrDecoderTest {
    static final byte[] SECRET="fixture-signing-secret-32-bytes!!".getBytes(StandardCharsets.UTF_8);
    static final UUID ID=UUID.randomUUID();
    static String envelope(byte[] bytes) throws Exception {
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(SECRET,"HmacSHA256"));
        var b64=Base64.getUrlEncoder().withoutPadding();return b64.encodeToString(bytes)+"."+b64.encodeToString(mac.doFinal(bytes));
    }
    static String signed(String payload) throws Exception{return envelope(payload.getBytes(StandardCharsets.UTF_8));}
    @Test void decodesAuthenticUnicodeAndEscapedName() throws Exception {
        var result=new SignedQrDecoder(SECRET).decode(signed("{\"id\":\""+ID+"\",\"name\":\"Nguyễn \\\"A\\\"\"}"));
        assertEquals(ID,result.ticketId());assertEquals("Nguyễn \"A\"",result.name());
    }
    @Test void rejectsSingleBitChangedSignature() throws Exception {
        String qr=signed("{\"id\":\""+ID+"\",\"name\":\"A\"}");String[] parts=qr.split("\\.");
        byte[] sig=Base64.getUrlDecoder().decode(parts[1]);sig[5]^=1;
        invalid(parts[0]+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(sig));
    }
    @Test void rejectsMissingDuplicateUnknownOrNonStringFields() throws Exception {
        for(String json:List.of("{\"name\":\"A\"}","{\"id\":\""+ID+"\"}",
                "{\"id\":\""+ID+"\",\"id\":\""+ID+"\",\"name\":\"A\"}",
                "{\"id\":\""+ID+"\",\"name\":[]}","{\"id\":\"1\",\"name\":\"A\"}",
                "{\"id\":\""+ID+"\",\"name\":\"A\",\"admin\":\"true\"}")) invalid(signed(json));
    }
    @Test void rejectsMalformedEnvelopePaddingAndOversizeBeforeParsing() {
        for(String qr:List.of("", ".", "a.b.c", "a=.b", "%%%.___", "A".repeat(9000)+".a"))invalid(qr);
    }
    @Test void refusesMalformedUtf8EvenWithValidSignature() throws Exception {invalid(envelope(new byte[]{(byte)0xc3,(byte)0x28}));}
    @Test void secretMustBeStrongAndCopied() throws Exception {
        assertThrows(IllegalArgumentException.class,()->new SignedQrDecoder(new byte[3]));
        byte[] secret=SECRET.clone();var decoder=new SignedQrDecoder(secret);Arrays.fill(secret,(byte)0);
        assertEquals(ID,decoder.decode(signed("{\"id\":\""+ID+"\",\"name\":\"A\"}")).ticketId());
    }
    private static void invalid(String qr){var e=assertThrows(CheckInException.class,()->new SignedQrDecoder(SECRET).decode(qr));assertEquals(400,e.httpStatus());assertEquals("INVALID_SIGNATURE",e.code());}
}
