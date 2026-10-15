package vn.ticketscenter.fulfillment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import vn.ticketscenter.order.model.OrderItem;
import vn.ticketscenter.persistence.model.BaseEntity;

import java.time.Instant;

@Entity
@Table(name = "tc_tickets", schema = "dbo")
public class Ticket extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    @Column(name = "ticket_code", nullable = false, unique = true, length = 120)
    private String ticketCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TicketStatus status = TicketStatus.ACTIVE;

    @Column(name = "paid_amount", nullable = false)
    private long paidAmount;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();

    @Column(name = "used_at")
    private Instant usedAt;

    protected Ticket() { }

    public Ticket(OrderItem orderItem, String ticketCode, long paidAmount) {
        this.orderItem = orderItem;
        this.ticketCode = ticketCode;
        this.paidAmount = paidAmount;
    }

    public OrderItem getOrderItem() { return orderItem; }
    public String getTicketCode() { return ticketCode; }
    public TicketStatus getStatus() { return status; }
    public long getPaidAmount() { return paidAmount; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getUsedAt() { return usedAt; }
}
