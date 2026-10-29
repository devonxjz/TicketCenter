package vn.ticketscenter.config.worker;

import java.math.BigDecimal;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.integration.mail.EmailMessage;
import vn.ticketscenter.identity.integration.mail.EmailSender;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Builds and sends the email for an outbox row. Unknown event types throw so the row goes to FAILED. */
public final class OutboxEmailJob implements OutboxWorker.EmailJob {
    public record Context(
            String recipient,
            String name,
            String orderCode,
            String eventTitle,
            BigDecimal amount) {}

    public interface ContextLoader {
        Context load(UUID outboxId, String eventType);
    }

    private final ContextLoader loader;
    private final EmailSender sender;

    public OutboxEmailJob(ContextLoader loader, EmailSender sender) {
        this.loader = loader;
        this.sender = sender;
    }

    public static ContextLoader databaseLoader(TransactionManager tx) {
        return (id, type) ->
                tx.execute(
                        DatabasePrincipal.WORKER,
                        em -> {
                            @SuppressWarnings("unchecked")
                            List<Object[]> rows =
                                    em.createNativeQuery(
                                                    """
                    SELECT recipient_email,recipient_name,order_code,event_title,amount
                    FROM dbo.vw_OutboxEmailContext WHERE outbox_id=:id AND event_type=:t""")
                                            .setParameter("id", id)
                                            .setParameter("t", type)
                                            .getResultList();
                            if (rows.isEmpty()) {
                                return null;
                            }
                            Object[] r = rows.get(0);
                            return new Context(
                                    (String) r[0],
                                    (String) r[1],
                                    (String) r[2],
                                    (String) r[3],
                                    (BigDecimal) r[4]);
                        });
    }

    @Override
    public void send(UUID outboxId, String eventType, String payload) {
        String subject;
        String line;
        switch (eventType) {
            case "ORDER_PAID" -> {
                subject = "Thanh toán thành công đơn %s";
                line =
                        "Đơn hàng %s cho sự kiện \"%s\" đã được thanh toán %s. Vé của bạn đã sẵn sàng trong tài khoản.";
            }
            case "REFUND_SUCCEEDED" -> {
                subject = "Hoàn tiền thành công đơn %s";
                line = "Đơn hàng %s cho sự kiện \"%s\" đã được hoàn %s.";
            }
            default ->
                    throw new IllegalArgumentException(
                            "Unsupported outbox event type: " + eventType);
        }
        Context c = loader.load(outboxId, eventType);
        if (c == null) {
            throw new IllegalStateException("No email context for outbox row " + outboxId);
        }
        String money = NumberFormat.getInstance(Locale.of("vi", "VN")).format(c.amount()) + " VND";
        String body =
                "Xin chào "
                        + c.name()
                        + ",\n\n"
                        + line.formatted(c.orderCode(), c.eventTitle(), money)
                        + "\n\nTicketCenter";
        sender.send(new EmailMessage(c.recipient(), subject.formatted(c.orderCode()), body, null));
    }
}
