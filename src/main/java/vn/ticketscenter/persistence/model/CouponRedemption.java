package vn.ticketscenter.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import vn.ticketscenter.identity.model.User;
import vn.ticketscenter.order.model.Coupon;
import vn.ticketscenter.order.model.Order;

import java.util.UUID;

@Entity
@Table(
        name = "tc_coupon_redemptions",
        schema = "dbo",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_coupon_redemption_order",
                        columnNames = {"coupon_id", "order_id"}))
public class CouponRedemption {
    public enum Status {
        RESERVED,
        CONSUMED,
        RELEASED
    }

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.RESERVED;

    protected CouponRedemption() {}

    public CouponRedemption(Coupon coupon, Order order, User user) {
        this.id = UUID.randomUUID();
        this.coupon = coupon;
        this.order = order;
        this.user = user;
    }

    public UUID getId() {
        return id;
    }

    public Coupon getCoupon() {
        return coupon;
    }

    public Order getOrder() {
        return order;
    }

    public User getUser() {
        return user;
    }

    public Status getStatus() {
        return status;
    }
}
