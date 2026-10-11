package vn.ticketscenter.fulfillment.checkin.security;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.fulfillment.checkin.CheckInException;
import vn.ticketscenter.fulfillment.ticket.service.SignedTicketQrSigner;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TicketQrInteroperabilityTest {
    @Test void realIssuerPngIsAcceptedByConfiguredScannerWithDedicatedKey() throws Exception {
        withProperties("dedicated-qr-signing-key-at-least-32-bytes", "legacy-ticket-secret-at-least-32-bytes",
                "application-secret-at-least-32-bytes", () -> {
            UUID id=UUID.randomUUID();
            String name="Sự kiện "+"Đ".repeat(242)+" - "+"K".repeat(120);
            var issuer=SignedTicketQrSigner.configured();
            byte[] png=SignedTicketQrSigner.generateQrPng(issuer.buildEnvelope(id,name),600,600);
            var bitmap=new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(
                    ImageIO.read(new ByteArrayInputStream(png)))));
            var decoded=SignedQrDecoder.configured().decode(new MultiFormatReader().decode(bitmap).getText());
            assertEquals(id,decoded.ticketId());assertEquals(name,decoded.name());
        });
    }

    @Test void existingApplicationKeyWorksForBothComponents() throws Exception {
        withProperties("", "", "application-secret-at-least-32-bytes", () -> {
            UUID id=UUID.randomUUID();
            assertEquals(id,SignedQrDecoder.configured().decode(SignedTicketQrSigner.configured().buildEnvelope(id,"A")).ticketId());
        });
    }

    @Test void weakExplicitKeyFailsClosedInsteadOfFallingBackToAnotherKey() throws Exception {
        withProperties("weak", "legacy-ticket-secret-at-least-32-bytes", "application-secret-at-least-32-bytes", () -> {
            assertThrows(IllegalStateException.class,SignedTicketQrSigner::configured);
            assertEquals(503,assertThrows(CheckInException.class,SignedQrDecoder::configured).httpStatus());
        });
    }

    private static void withProperties(String dedicated,String legacy,String app,Work work) throws Exception {
        String[] keys={"TC_TICKET_SIGNING_SECRET","ticket.qr.secret","app.secret.key"};
        String[] values={dedicated,legacy,app},previous=new String[keys.length];
        for(int i=0;i<keys.length;i++){previous[i]=System.getProperty(keys[i]);System.setProperty(keys[i],values[i]);}
        try { work.run(); } finally {
            for(int i=0;i<keys.length;i++)if(previous[i]==null)System.clearProperty(keys[i]);else System.setProperty(keys[i],previous[i]);
        }
    }
    @FunctionalInterface private interface Work { void run() throws Exception; }
}
