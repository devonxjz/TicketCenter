package vn.ticketscenter.settlement.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import vn.ticketscenter.persistence.model.BaseEntity;

import java.time.Instant;

@Entity
@Table(name = "tc_payouts", schema = "dbo")
public class Payout extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settlement_id", nullable = false)
    private Settlement settlement;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayoutStatus status = PayoutStatus.PENDING;

    @Column(name = "provider_reference", length = 200)
    private String providerReference;

    @Column(name = "failure_reason", length = 2000)
    private String failureReason;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Payout() { }

    public Payout(Settlement settlement, long amount) {
        this.settlement = settlement;
        this.amount = amount;
    }

    public Settlement getSettlement() { return settlement; }
    public long getAmount() { return amount; }
    public PayoutStatus getStatus() { return status; }
    public String getProviderReference() { return providerReference; }
    public String getFailureReason() { return failureReason; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
