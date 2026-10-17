package vn.ticketscenter.fulfillment.model;

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
import vn.ticketscenter.order.model.Order;
import vn.ticketscenter.order.model.Payment;
import vn.ticketscenter.persistence.model.BaseEntity;
import vn.ticketscenter.persistence.model.RefundTicket;
import vn.ticketscenter.persistence.model.RefundTransferLog;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tc_refund_obligations", schema = "dbo")
public class Refund extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RefundPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_type", length = 30)
    private RefundReason reasonType;

    @Column(length = 2000)
    private String reason;

    @Column(name = "rejection_reason", length = 2000)
    private String rejectionReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private RefundStatus status = RefundStatus.REQUESTED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id")
    private vn.ticketscenter.identity.model.User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    private vn.ticketscenter.identity.model.User reviewer;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "current_attempt_id")
    private UUID currentAttemptId;

    @Column(name = "provider_reference", length = 200)
    private String providerReference;

    @Column(name = "processed_at")
    private Instant processedAt;

    @OneToMany(mappedBy = "refund", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RefundTicket> tickets = new ArrayList<>();

    @OneToMany(mappedBy = "refund")
    private List<RefundTransferLog> transferLogs = new ArrayList<>();

    protected Refund() {}

    public Refund(
            Order order,
            Payment payment,
            long amount,
            RefundPurpose purpose,
            RefundReason reasonType,
            String reason) {
        this.order = order;
        this.payment = payment;
        this.amount = amount;
        this.purpose = purpose;
        this.reasonType = reasonType;
        this.reason = reason;
    }

    public vn.ticketscenter.identity.model.User getRequester() {
        return requester;
    }

    public vn.ticketscenter.identity.model.User getReviewer() {
        return reviewer;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Order getOrder() {
        return order;
    }

    public Payment getPayment() {
        return payment;
    }

    public long getAmount() {
        return amount;
    }

    public RefundPurpose getPurpose() {
        return purpose;
    }

    public RefundReason getReasonType() {
        return reasonType;
    }

    public String getReason() {
        return reason;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public UUID getCurrentAttemptId() {
        return currentAttemptId;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public List<RefundTicket> getTickets() {
        return List.copyOf(tickets);
    }
}
