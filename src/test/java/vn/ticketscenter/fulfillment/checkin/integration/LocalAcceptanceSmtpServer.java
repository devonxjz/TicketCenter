package vn.ticketscenter.fulfillment.checkin.integration;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;

/** Loopback-only STARTTLS capture for the existing SMTP regression tests. Never relays mail. */
public final class LocalAcceptanceSmtpServer {
    public static void main(String[] args) throws Exception {
        var store = KeyStore.getInstance("PKCS12");
        char[] password = "local-fixture-only".toCharArray();
        try (var in = Files.newInputStream(Path.of(args[0]))) { store.load(in, password); }
        var keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(store, password);
        var context = SSLContext.getInstance("TLS");
        context.init(keys.getKeyManagers(), null, null);
        int accepted = 0;
        try (var server = new ServerSocket(18825, 10, InetAddress.getByName("127.0.0.1"))) {
            System.out.println("READY loopback SMTP STARTTLS");
            while (true) {
                try (var socket = server.accept()) {
                    socket.setSoTimeout(5000);
                    var channel = new Channel(socket);
                    channel.reply("220 local acceptance capture");
                    channel.expect("EHLO "); channel.reply("250-STARTTLS\r\n250 AUTH LOGIN");
                    channel.expect("STARTTLS"); channel.reply("220 Ready for TLS");
                    try (var tls = (SSLSocket) context.getSocketFactory().createSocket(socket, "127.0.0.1", 18825, true)) {
                        tls.setUseClientMode(false); tls.setSoTimeout(5000); tls.startHandshake();
                        channel = new Channel(tls);
                        channel.expect("EHLO "); channel.reply("250 AUTH LOGIN");
                        channel.expect("AUTH LOGIN"); channel.reply("334 VXNlcm5hbWU6");
                        channel.expect("Y2FwdHVyZUBmaXh0dXJlLmludmFsaWQ="); channel.reply("334 UGFzc3dvcmQ6");
                        channel.expect("bG9jYWwtZml4dHVyZS1vbmx5"); channel.reply("235 Authenticated");
                        channel.expect("MAIL FROM:<capture@fixture.invalid>"); channel.reply("250 OK");
                        channel.expect("RCPT TO:<"); channel.reply("250 Captured locally");
                        channel.expect("DATA"); channel.reply("354 End with dot");
                        int lines = 0; boolean subject = false, contentType = false;
                        String line;
                        while (!(line = channel.read()).equals(".")) {
                            if (++lines > 10000) throw new IOException("Message exceeds fixture limit");
                            subject |= line.startsWith("Subject: ");
                            contentType |= line.startsWith("Content-Type: ");
                        }
                        if (!subject || !contentType || lines < 7) throw new IOException("Incomplete MIME message");
                        Files.writeString(Path.of(args[1]), Integer.toString(++accepted), StandardCharsets.UTF_8);
                        channel.reply("250 Message captured"); channel.expect("QUIT");
                    }
                } catch (Exception failure) {
                    System.err.println("SMTP capture rejected a session: " + failure.getClass().getSimpleName());
                }
            }
        }
    }
    private static final class Channel {
        final BufferedReader reader;
        final BufferedWriter writer;
        Channel(Socket socket) throws IOException {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }
        String read() throws IOException {
            String line = reader.readLine();
            if (line == null) throw new EOFException("SMTP client disconnected");
            return line;
        }
        void expect(String prefix) throws IOException {
            if (!read().startsWith(prefix)) throw new IOException("Unexpected SMTP command");
        }
        void reply(String line) throws IOException { writer.write(line + "\r\n"); writer.flush(); }
    }
}
