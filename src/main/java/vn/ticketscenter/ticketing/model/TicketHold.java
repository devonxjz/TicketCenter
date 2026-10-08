package vn.ticketscenter.ticketing.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import vn.ticketscenter.event.model.Event;
import vn.ticketscenter.identity.model.User;
import vn.ticketscenter.persistence.model.BaseEntity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tc_ticket_holds", schema = "dbo")
public class TicketHold extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HoldStatus status = HoldStatus.ACTIVE;

    @OneToMany(mappedBy = "ticketHold", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TicketHoldItem> items = new ArrayList<>();

    protected TicketHold() { }

    public TicketHold(User buyer, Event event, Instant expiresAt) {
        this.buyer = buyer;
        this.event = event;
        this.expiresAt = expiresAt;
    }

    public User getBuyer() { return buyer; }
    public Event getEvent() { return event; }
    public Instant getExpiresAt() { return expiresAt; }
    public HoldStatus getStatus() { return status; }
    public List<TicketHoldItem> getItems() { return List.copyOf(items); }
}
