package vn.ticketscenter.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import vn.ticketscenter.settlement.model.Settlement;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tc_settlement_transfer_logs", schema = "dbo")
public class SettlementTransferLog {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settlement_id", nullable = false)
    private Settlement settlement;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false, length = 30)
    private String result;

    @Column(name = "provider_reference", length = 200)
    private String providerReference;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected SettlementTransferLog() { }

    public SettlementTransferLog(Settlement settlement, UUID payoutId, long amount, String result) {
        this.id = payoutId;
        this.settlement = settlement;
        this.amount = amount;
        this.result = result;
    }

    public UUID getId() { return id; }
    public Settlement getSettlement() { return settlement; }
    public long getAmount() { return amount; }
    public String getResult() { return result; }
    public String getProviderReference() { return providerReference; }
    public Instant getProcessedAt() { return processedAt; }
}
