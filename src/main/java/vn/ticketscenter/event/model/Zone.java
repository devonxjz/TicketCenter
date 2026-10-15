package vn.ticketscenter.event.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import vn.ticketscenter.persistence.model.BaseEntity;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tc_zones", schema = "dbo",
        uniqueConstraints = @UniqueConstraint(name = "uq_zone_event_name", columnNames = {"event_id", "name"}))
public class Zone extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ZoneType type;

    @Column(nullable = false)
    private long price;

    @Column(name = "standing_capacity")
    private Integer standingCapacity;

    @Column(name = "standing_held", nullable = false)
    private int standingHeld;

    @Column(name = "standing_sold", nullable = false)
    private int standingSold;

    @OneToMany(mappedBy = "zone")
    private List<Seat> seats = new ArrayList<>();

    protected Zone() { }

    public Zone(Event event, String name, ZoneType type, long price) {
        this.event = event;
        this.name = name;
        this.type = type;
        this.price = price;
    }

    public Event getEvent() { return event; }
    public String getName() { return name; }
    public ZoneType getType() { return type; }
    public long getPrice() { return price; }
    public Integer getStandingCapacity() { return standingCapacity; }
    public int getStandingHeld() { return standingHeld; }
    public int getStandingSold() { return standingSold; }
    public List<Seat> getSeats() { return List.copyOf(seats); }
}
