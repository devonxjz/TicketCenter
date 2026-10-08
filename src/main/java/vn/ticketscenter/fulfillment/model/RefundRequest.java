package vn.ticketscenter.fulfillment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import vn.ticketscenter.identity.model.User;
import vn.ticketscenter.order.model.Order;
import vn.ticketscenter.persistence.model.BaseEntity;

@Entity
@Table(name = "tc_refund_requests", schema = "dbo")
public class RefundRequest extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @Column(length = 2000)
    private String reason;

    protected RefundRequest() { }

    public RefundRequest(Order order, User requester, String reason) {
        this.order = order;
        this.requester = requester;
        this.reason = reason;
    }

    public Order getOrder() { return order; }
    public User getRequester() { return requester; }
    public String getReason() { return reason; }
}
