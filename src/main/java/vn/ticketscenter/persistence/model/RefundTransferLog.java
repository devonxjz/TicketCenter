package vn.ticketscenter.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import vn.ticketscenter.fulfillment.model.Refund;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tc_refund_transfer_logs", schema = "dbo")
public class RefundTransferLog {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "refund_id", nullable = false)
    private Refund refund;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Column(nullable = false, length = 30)
    private String result;

    @Column(name = "provider_reference", length = 200)
    private String providerReference;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "failure_reason", length = 2000)
    private String failureReason;

    protected RefundTransferLog() { }

    public RefundTransferLog(Refund refund, UUID attemptId, int attemptNo, String result) {
        this.id = attemptId;
        this.refund = refund;
        this.attemptNo = attemptNo;
        this.result = result;
    }

    public UUID getId() { return id; }
    public Refund getRefund() { return refund; }
    public int getAttemptNo() { return attemptNo; }
    public String getResult() { return result; }
    public String getProviderReference() { return providerReference; }
    public Instant getProcessedAt() { return processedAt; }
    public String getFailureReason() { return failureReason; }
}
