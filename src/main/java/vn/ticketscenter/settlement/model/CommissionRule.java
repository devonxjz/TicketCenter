package vn.ticketscenter.settlement.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import vn.ticketscenter.identity.model.Organization;
import vn.ticketscenter.persistence.model.BaseEntity;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "tc_commission_rules", schema = "dbo")
public class CommissionRule extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "rate_percent", nullable = false, precision = 12, scale = 4)
    private BigDecimal ratePercent;

    @Column(name = "fixed_fee", nullable = false)
    private long fixedFee;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to", nullable = false)
    private Instant effectiveTo;

    protected CommissionRule() { }

    public CommissionRule(Organization organization, BigDecimal ratePercent, long fixedFee,
                           Instant effectiveFrom, Instant effectiveTo) {
        this.organization = organization;
        this.ratePercent = ratePercent;
        this.fixedFee = fixedFee;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }

    public Organization getOrganization() { return organization; }
    public BigDecimal getRatePercent() { return ratePercent; }
    public long getFixedFee() { return fixedFee; }
    public Instant getEffectiveFrom() { return effectiveFrom; }
    public Instant getEffectiveTo() { return effectiveTo; }
}
