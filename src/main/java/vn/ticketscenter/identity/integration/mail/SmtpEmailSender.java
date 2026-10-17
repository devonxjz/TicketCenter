package vn.ticketscenter.identity.integration.mail;

import vn.ticketscenter.config.MailConfig;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Triển khai gửi email qua giao thức SMTP bằng Socket thuần chuẩn JDK (cổng 587 STARTTLS hoặc 465 SSL).
 * Đảm bảo timeout hữu hạn 5 giây và không phụ thuộc thư viện ngoài.
 */
public class SmtpEmailSender implements EmailSender {

    private static final Logger LOGGER = Logger.getLogger(SmtpEmailSender.class.getName());
    private static final int SOCKET_TIMEOUT_MS = 5000;
    private static final Base64.Encoder MIME_ENCODER =
            Base64.getMimeEncoder(76, new byte[] {'\r', '\n'});

    @Override
    public void send(EmailMessage message) throws EmailDeliveryException {
        String host = MailConfig.getHost();
        int port = MailConfig.getPort();
        String user = MailConfig.getUser();
        String password = MailConfig.getPassword();

        if (user == null || user.isBlank() || password == null || password.isBlank()) {
            LOGGER.log(
                    Level.WARNING,
                    "Cấu hình SMTP chưa đầy đủ, không thể gửi mail tới {0}",
                    message.recipient());
            throw new EmailDeliveryException("SMTP credentials are not configured");
        }

        try {
            if (port == 465) {
                sendViaSsl(host, port, user, password, message);
            } else {
                sendViaStartTls(host, port, user, password, message);
            }
        } catch (Exception ex) {
            LOGGER.log(
                    Level.WARNING,
                    "Gửi email thất bại tới " + message.recipient() + ": " + ex.getMessage());
            throw new EmailDeliveryException("Không thể gửi email tới " + message.recipient(), ex);
        }
    }

    private void sendViaStartTls(
            String host, int port, String user, String password, EmailMessage message)
            throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), SOCKET_TIMEOUT_MS);
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);

            BufferedReader reader = reader(socket);
            BufferedWriter writer = writer(socket);

            expectReply(reader, "220");
            sendCommand(writer, "EHLO " + host);
            readUntilReady(reader);

            sendCommand(writer, "STARTTLS");
            expectReply(reader, "220");

            SSLSocketFactory sslFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            try (SSLSocket sslSocket =
                    (SSLSocket) sslFactory.createSocket(socket, host, port, true)) {
                sslSocket.setSoTimeout(SOCKET_TIMEOUT_MS);
                sslSocket.startHandshake();

                BufferedReader sslReader = reader(sslSocket);
                BufferedWriter sslWriter = writer(sslSocket);

                sendCommand(sslWriter, "EHLO " + host);
                readUntilReady(sslReader);

                authenticateAndSend(sslReader, sslWriter, user, password, message);
            }
        }
    }

    private void sendViaSsl(
            String host, int port, String user, String password, EmailMessage message)
            throws IOException {
        SSLSocketFactory sslFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        try (SSLSocket socket = (SSLSocket) sslFactory.createSocket()) {
            socket.connect(new InetSocketAddress(host, port), SOCKET_TIMEOUT_MS);
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);
            socket.startHandshake();

            BufferedReader reader = reader(socket);
            BufferedWriter writer = writer(socket);

            expectReply(reader, "220");
            sendCommand(writer, "EHLO " + host);
            readUntilReady(reader);

            authenticateAndSend(reader, writer, user, password, message);
        }
    }

    private static BufferedReader reader(Socket socket) throws IOException {
        return new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    }

    private static BufferedWriter writer(Socket socket) throws IOException {
        return new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    private void authenticateAndSend(
            BufferedReader reader,
            BufferedWriter writer,
            String user,
            String password,
            EmailMessage message)
            throws IOException {
        sendCommand(writer, "AUTH LOGIN");
        expectReply(reader, "334");

        sendCommand(
                writer, Base64.getEncoder().encodeToString(user.getBytes(StandardCharsets.UTF_8)));
        expectReply(reader, "334");

        sendCommand(
                writer,
                Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8)));
        expectReply(reader, "235");

        sendCommand(writer, "MAIL FROM:<" + user + ">");
        expectReply(reader, "250");

        sendCommand(writer, "RCPT TO:<" + message.recipient() + ">");
        expectReply(reader, "250");

        sendCommand(writer, "DATA");
        expectReply(reader, "354");

        writer.write(buildMimeMessage(user, message));
        writer.write("\r\n.\r\n");
        writer.flush();
        expectReply(reader, "250");

        sendCommand(writer, "QUIT");
    }

    /**
     * Dựng phần header + body MIME. Nội dung luôn mã hóa base64 (dòng 76 ký tự, CRLF) nên không
     * vướng giới hạn 998 ký tự/dòng, ký tự xuống dòng trần hay dấu chấm đầu dòng của SMTP DATA.
     * Có cả text và html thì gửi multipart/alternative để client không hiển thị HTML vẫn đọc được.
     */
    static String buildMimeMessage(String user, EmailMessage message) {
        boolean hasText = message.bodyText() != null && !message.bodyText().isBlank();
        boolean hasHtml = message.bodyHtml() != null && !message.bodyHtml().isBlank();
        String domain =
                user.contains("@") ? user.substring(user.indexOf('@') + 1) : "ticketscenter.local";

        StringBuilder mime = new StringBuilder();
        mime.append("From: TicketsCenter <").append(user).append(">\r\n");
        mime.append("To: <").append(message.recipient()).append(">\r\n");
        mime.append("Subject: =?UTF-8?B?")
                .append(
                        Base64.getEncoder()
                                .encodeToString(message.subject().getBytes(StandardCharsets.UTF_8)))
                .append("?=\r\n");
        String date =
                DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC));
        mime.append("Date: ").append(date).append("\r\n");
        mime.append("Message-ID: <")
                .append(UUID.randomUUID())
                .append('@')
                .append(domain)
                .append(">\r\n");
        mime.append("MIME-Version: 1.0\r\n");

        if (hasText && hasHtml) {
            String boundary = "tc-alt-" + UUID.randomUUID();
            mime.append("Content-Type: multipart/alternative; boundary=\"")
                    .append(boundary)
                    .append("\"\r\n\r\n");
            appendPart(
                    mime.append("--").append(boundary).append("\r\n"),
                    "text/plain",
                    message.bodyText());
            appendPart(
                    mime.append("--").append(boundary).append("\r\n"),
                    "text/html",
                    message.bodyHtml());
            mime.append("--").append(boundary).append("--");
        } else {
            appendPart(
                    mime,
                    hasHtml ? "text/html" : "text/plain",
                    hasHtml ? message.bodyHtml() : message.bodyText());
        }
        return mime.toString();
    }

    private static void appendPart(StringBuilder mime, String contentType, String body) {
        mime.append("Content-Type: ").append(contentType).append("; charset=UTF-8\r\n");
        mime.append("Content-Transfer-Encoding: base64\r\n\r\n");
        mime.append(MIME_ENCODER.encodeToString(body.getBytes(StandardCharsets.UTF_8)))
                .append("\r\n");
    }

    private void sendCommand(BufferedWriter writer, String command) throws IOException {
        writer.write(command + "\r\n");
        writer.flush();
    }

    private void expectReply(BufferedReader reader, String expectedCode) throws IOException {
        String line = reader.readLine();
        if (line == null || !line.startsWith(expectedCode)) {
            throw new IOException(
                    "Unexpected SMTP reply: " + line + " (expected: " + expectedCode + ")");
        }
    }

    private void readUntilReady(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.length() >= 4 && line.charAt(3) == ' ') {
                break;
            }
        }
    }
}
