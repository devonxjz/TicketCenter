package vn.ticketscenter.settlement.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import vn.ticketscenter.event.model.Event;
import vn.ticketscenter.persistence.model.BaseEntity;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tc_settlements", schema = "dbo")
public class Settlement extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, unique = true)
    private Event event;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SettlementStatus status = SettlementStatus.DRAFT;

    @Column(name = "gross_revenue", nullable = false)
    private long grossRevenue;

    @Column(name = "total_refund", nullable = false)
    private long totalRefund;

    @Column(name = "total_commission", nullable = false)
    private long totalCommission;

    @Column(name = "paid_amount", nullable = false)
    private long paidAmount;

    @Column(name = "pending_amount", nullable = false)
    private long pendingAmount;

    @OneToMany(mappedBy = "settlement", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SettlementItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "settlement")
    private List<Payout> payouts = new ArrayList<>();

    protected Settlement() { }

    public Settlement(Event event) { this.event = event; }

    public Event getEvent() { return event; }
    public SettlementStatus getStatus() { return status; }
    public long getGrossRevenue() { return grossRevenue; }
    public long getTotalRefund() { return totalRefund; }
    public long getTotalCommission() { return totalCommission; }
    public long getPaidAmount() { return paidAmount; }
    public long getPendingAmount() { return pendingAmount; }
    public long getNetPayable() { return grossRevenue - totalRefund - totalCommission; }
    public long getAvailableToPay() { return getNetPayable() - paidAmount - pendingAmount; }
    public List<SettlementItem> getItems() { return List.copyOf(items); }
    public List<Payout> getPayouts() { return List.copyOf(payouts); }
}
