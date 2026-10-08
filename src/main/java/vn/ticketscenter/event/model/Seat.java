package vn.ticketscenter.event.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import vn.ticketscenter.persistence.model.BaseEntity;

@Entity
@Table(name = "tc_seats", schema = "dbo",
        uniqueConstraints = @UniqueConstraint(name = "uq_seat_zone_row_number", columnNames = {"zone_id", "row_name", "seat_number"}))
public class Seat extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    @Column(name = "row_name", nullable = false, length = 50)
    private String rowName;

    @Column(name = "seat_number", nullable = false)
    private int seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatStatus status = SeatStatus.AVAILABLE;

    protected Seat() { }

    public Seat(Zone zone, String rowName, int seatNumber) {
        this.zone = zone;
        this.rowName = rowName;
        this.seatNumber = seatNumber;
    }

    public Zone getZone() { return zone; }
    public String getRowName() { return rowName; }
    public int getSeatNumber() { return seatNumber; }
    public SeatStatus getStatus() { return status; }
}
