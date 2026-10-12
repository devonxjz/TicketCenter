package vn.ticketscenter.fulfillment.checkin.integration;
import java.nio.file.*;
import java.util.Properties;
import com.google.zxing.*;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
/** Actual, signed QR images for disposable browser acceptance only. Never an issuer API. */
public final class ScanWebFixture {
    public static void main(String[] args)throws Exception{
        var f=new ScanFixture();var p=new Properties();p.setProperty("staff",f.staff.toString());p.setProperty("other",f.other.toString());p.setProperty("event",f.event.toString());p.setProperty("ticket",f.tickets.getFirst().toString());p.setProperty("code","K02-"+f.tickets.getFirst());
        Path out=Path.of("target/khanh02-web-fixture.properties");try(var w=Files.newBufferedWriter(out)){p.store(w,"Disposable local identities only");}
        MatrixToImageWriter.writeToPath(new QRCodeWriter().encode(f.qr(f.tickets.getLast()),BarcodeFormat.QR_CODE,480,480),"PNG",Path.of("target/khanh02-fixture-qr.png"));
        Path json=Path.of("target/khanh02-web-qr.txt");Files.writeString(json,f.qr(f.tickets.getFirst()));
    }
}
