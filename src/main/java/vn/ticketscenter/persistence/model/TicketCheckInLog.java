package vn.ticketscenter.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import vn.ticketscenter.event.model.Event;
import vn.ticketscenter.fulfillment.model.CheckInResult;
import vn.ticketscenter.fulfillment.model.Ticket;
import vn.ticketscenter.identity.model.User;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tc_ticket_check_in_logs", schema = "dbo")
public class TicketCheckInLog {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CheckInResult result;

    @Column(name = "scanned_at", nullable = false)
    private Instant scannedAt;

    @Column(name = "failure_reason", length = 2000)
    private String failureReason;

    protected TicketCheckInLog() {}

    public TicketCheckInLog(
            Event event, Ticket ticket, User actor, CheckInResult result, Instant scannedAt) {
        this.id = UUID.randomUUID();
        this.event = event;
        this.ticket = ticket;
        this.actor = actor;
        this.result = result;
        this.scannedAt = scannedAt;
    }

    public UUID getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public User getActor() {
        return actor;
    }

    public CheckInResult getResult() {
        return result;
    }

    public Instant getScannedAt() {
        return scannedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
