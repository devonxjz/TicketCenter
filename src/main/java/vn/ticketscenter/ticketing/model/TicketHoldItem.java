package vn.ticketscenter.ticketing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import vn.ticketscenter.event.model.Seat;
import vn.ticketscenter.event.model.Zone;
import vn.ticketscenter.persistence.model.BaseEntity;

@Entity
@Table(name = "tc_ticket_hold_items", schema = "dbo")
public class TicketHoldItem extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_hold_id", nullable = false)
    private TicketHold ticketHold;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id")
    private Seat seat;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false)
    private long unitPrice;

    protected TicketHoldItem() { }

    public TicketHoldItem(TicketHold ticketHold, Zone zone, Seat seat, int quantity, long unitPrice) {
        this.ticketHold = ticketHold;
        this.zone = zone;
        this.seat = seat;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public TicketHold getTicketHold() { return ticketHold; }
    public Zone getZone() { return zone; }
    public Seat getSeat() { return seat; }
    public int getQuantity() { return quantity; }
    public long getUnitPrice() { return unitPrice; }
}
