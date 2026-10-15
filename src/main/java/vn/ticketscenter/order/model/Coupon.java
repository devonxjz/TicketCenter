package vn.ticketscenter.order.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import vn.ticketscenter.identity.model.Organization;
import vn.ticketscenter.persistence.model.BaseEntity;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "tc_coupons", schema = "dbo",
        uniqueConstraints = @UniqueConstraint(name = "uq_coupon_org_code", columnNames = {"organization_id", "code"}))
public class Coupon extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 80)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 30)
    private DiscountType discountType;

    @Column(name = "fixed_amount")
    private Long fixedAmount;

    @Column(precision = 12, scale = 4)
    private BigDecimal percentage;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_to", nullable = false)
    private Instant validTo;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "max_uses", nullable = false)
    private int maxUses;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    protected Coupon() { }

    public Coupon(Organization organization, String code, DiscountType discountType,
                  Long fixedAmount, BigDecimal percentage, Instant validFrom,
                  Instant validTo, int maxUses) {
        this.organization = organization;
        this.code = code;
        this.discountType = discountType;
        this.fixedAmount = fixedAmount;
        this.percentage = percentage;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.maxUses = maxUses;
    }

    public Organization getOrganization() { return organization; }
    public String getCode() { return code; }
    public DiscountType getDiscountType() { return discountType; }
    public Long getFixedAmount() { return fixedAmount; }
    public BigDecimal getPercentage() { return percentage; }
    public Instant getValidFrom() { return validFrom; }
    public Instant getValidTo() { return validTo; }
    public boolean isActive() { return active; }
    public int getMaxUses() { return maxUses; }
    public int getUsedCount() { return usedCount; }
}
