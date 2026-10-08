package vn.ticketscenter.order.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import vn.ticketscenter.event.model.Seat;
import vn.ticketscenter.event.model.Zone;
import vn.ticketscenter.fulfillment.model.Ticket;
import vn.ticketscenter.persistence.model.BaseEntity;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tc_order_items", schema = "dbo")
public class OrderItem extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id")
    private Seat seat;

    @Column(name = "zone_name_snapshot", nullable = false, length = 200)
    private String zoneNameSnapshot;

    @Column(name = "seat_label_snapshot", length = 100)
    private String seatLabelSnapshot;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false)
    private long unitPrice;

    @OneToMany(mappedBy = "orderItem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Ticket> tickets = new ArrayList<>();

    protected OrderItem() { }

    public OrderItem(Order order, Zone zone, Seat seat, String zoneNameSnapshot,
                     String seatLabelSnapshot, int quantity, long unitPrice) {
        this.order = order;
        this.zone = zone;
        this.seat = seat;
        this.zoneNameSnapshot = zoneNameSnapshot;
        this.seatLabelSnapshot = seatLabelSnapshot;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public Order getOrder() { return order; }
    public Zone getZone() { return zone; }
    public Seat getSeat() { return seat; }
    public String getZoneNameSnapshot() { return zoneNameSnapshot; }
    public String getSeatLabelSnapshot() { return seatLabelSnapshot; }
    public int getQuantity() { return quantity; }
    public long getUnitPrice() { return unitPrice; }
    public long lineTotal() { return unitPrice * quantity; }
    public List<Ticket> getTickets() { return List.copyOf(tickets); }
}
