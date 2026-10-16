package vn.ticketscenter.settlement.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import vn.ticketscenter.order.model.Order;
import vn.ticketscenter.persistence.model.BaseEntity;

@Entity
@Table(name = "tc_settlement_order_snapshots", schema = "dbo")
public class SettlementItem extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settlement_id", nullable = false)
    private Settlement settlement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "gross_amount", nullable = false)
    private long grossAmount;

    @Column(name = "refund_amount", nullable = false)
    private long refundAmount;

    @Column(name = "commission_amount", nullable = false)
    private long commissionAmount;

    @Column(name = "net_amount", nullable = false)
    private long netAmount;

    protected SettlementItem() {}

    public SettlementItem(
            Settlement settlement,
            Order order,
            long grossAmount,
            long refundAmount,
            long commissionAmount,
            long netAmount) {
        this.settlement = settlement;
        this.order = order;
        this.grossAmount = grossAmount;
        this.refundAmount = refundAmount;
        this.commissionAmount = commissionAmount;
        this.netAmount = netAmount;
    }

    public Settlement getSettlement() {
        return settlement;
    }

    public Order getOrder() {
        return order;
    }

    public long getGrossAmount() {
        return grossAmount;
    }

    public long getRefundAmount() {
        return refundAmount;
    }

    public long getCommissionAmount() {
        return commissionAmount;
    }

    public long getNetAmount() {
        return netAmount;
    }
}
