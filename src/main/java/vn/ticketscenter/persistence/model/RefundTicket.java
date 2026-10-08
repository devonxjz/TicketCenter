package vn.ticketscenter.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import vn.ticketscenter.fulfillment.model.Refund;
import vn.ticketscenter.fulfillment.model.Ticket;

import java.util.UUID;

@Entity
@Table(name = "tc_refund_tickets", schema = "dbo",
        uniqueConstraints = @UniqueConstraint(name = "uq_refund_ticket", columnNames = {"refund_id", "ticket_id"}))
public class RefundTicket {
    @Id
    @Column(nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "refund_id", nullable = false)
    private Refund refund;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @Column(name = "is_open", nullable = false)
    private boolean open = true;

    protected RefundTicket() { }

    public RefundTicket(Refund refund, Ticket ticket) {
        this.id = UUID.randomUUID();
        this.refund = refund;
        this.ticket = ticket;
    }

    public UUID getId() { return id; }
    public Refund getRefund() { return refund; }
    public Ticket getTicket() { return ticket; }
    public boolean isOpen() { return open; }
}
