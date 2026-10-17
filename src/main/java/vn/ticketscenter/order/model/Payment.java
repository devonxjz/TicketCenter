package vn.ticketscenter.order.model;

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
@Table(name = "tc_payments", schema = "dbo")
public class Payment extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "txn_ref", nullable = false, unique = true, length = 200)
    private String txnRef;

    @Column(name = "transaction_no", length = 200)
    private String transactionNo;

    @Column(name = "paid_at")
    private Instant paidAt;

    protected Payment() {}

    private Payment(Order order, String txnRef) {
        this.order = order;
        this.amount = order.getTotalAmount();
        this.txnRef = txnRef;
    }

    public static Payment start(Order order, String txnRef) {
        return new Payment(order, txnRef);
    }

    public Order getOrder() {
        return order;
    }

    public long getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getTxnRef() {
        return txnRef;
    }

    public String getTransactionNo() {
        return transactionNo;
    }

    public Instant getPaidAt() {
        return paidAt;
    }
}
