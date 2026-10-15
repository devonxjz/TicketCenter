package vn.ticketscenter.order.model;

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
import jakarta.persistence.UniqueConstraint;
import vn.ticketscenter.event.model.Event;
import vn.ticketscenter.identity.model.User;
import vn.ticketscenter.persistence.model.BaseEntity;
import vn.ticketscenter.ticketing.model.TicketHold;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tc_orders", schema = "dbo",
        uniqueConstraints = @UniqueConstraint(name = "uq_order_code", columnNames = "order_code"))
public class Order extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_hold_id", nullable = false, unique = true)
    private TicketHold hold;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id")
    private Coupon coupon;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_payment_id")
    private Payment acceptedPayment;

    @Column(name = "order_code", nullable = false, unique = true, length = 50)
    private String orderCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    @Column(name = "discount_amount", nullable = false)
    private long discountAmount;

    @Column(name = "subtotal_amount", nullable = false)
    private long subtotalAmount;

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    @Column(name = "paid_at")
    private Instant paidAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order")
    private List<Payment> payments = new ArrayList<>();

    protected Order() { }

    public Order(User buyer, Event event, TicketHold hold, String orderCode,
                 long subtotalAmount, long discountAmount, long totalAmount) {
        this.buyer = buyer;
        this.event = event;
        this.hold = hold;
        this.orderCode = orderCode;
        this.subtotalAmount = subtotalAmount;
        this.discountAmount = discountAmount;
        this.totalAmount = totalAmount;
    }

    public User getBuyer() { return buyer; }
    public Event getEvent() { return event; }
    public TicketHold getHold() { return hold; }
    public Coupon getCoupon() { return coupon; }
    public Payment getAcceptedPayment() { return acceptedPayment; }
    public String getOrderCode() { return orderCode; }
    public OrderStatus getStatus() { return status; }
    public long getDiscountAmount() { return discountAmount; }
    public long getSubtotalAmount() { return subtotalAmount; }
    public long getTotalAmount() { return totalAmount; }
    public Instant getPaidAt() { return paidAt; }
    public List<OrderItem> getItems() { return List.copyOf(items); }
}
