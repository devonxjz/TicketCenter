package vn.ticketscenter.event.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import vn.ticketscenter.identity.model.Organization;
import vn.ticketscenter.settlement.model.CommissionRule;
import vn.ticketscenter.settlement.model.Settlement;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tc_events", schema = "dbo")
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commission_rule_id")
    private CommissionRule commissionRule;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, length = 5000)
    private String description;

    @Transient private EventCategory category;

    @Column(name = "venue_name", nullable = false, length = 300)
    private String venueName;

    @Column(name = "venue_address", nullable = false, length = 1000)
    private String venueAddress;

    @Column(name = "event_image", length = 2048)
    private String eventImage;

    @Column(name = "event_image_public_id", length = 255)
    private String eventImagePublicId;

    @Column(name = "sale_start", nullable = false)
    private Instant saleStart;

    @Column(name = "sale_end", nullable = false)
    private Instant saleEnd;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EventStatus status = EventStatus.DRAFT;

    @Column(nullable = false)
    private long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "rejection_reason", length = 2000)
    private String rejectionReason;

    @OneToMany(mappedBy = "event")
    private List<Zone> zones = new ArrayList<>();

    @OneToOne(mappedBy = "event", fetch = FetchType.LAZY)
    private Settlement settlement;

    protected Event() {}

    public Event(
            Organization organization,
            String title,
            String description,
            EventCategory category,
            String venueName,
            String venueAddress) {
        this.organization = organization;
        this.title = title;
        this.description = description;
        this.category = category;
        this.venueName = venueName;
        this.venueAddress = venueAddress;
    }

    public Event(
            Organization organization,
            String title,
            String description,
            EventCategory category,
            String venueName,
            String venueAddress,
            Instant startTime,
            Instant endTime) {
        this(organization, title, description, category, venueName, venueAddress);
        this.startTime = startTime;
        this.endTime = endTime;
        this.saleStart =
                (startTime != null) ? startTime.minus(java.time.Duration.ofDays(7)) : Instant.now();
        this.saleEnd =
                (startTime != null) ? startTime : Instant.now().plus(java.time.Duration.ofDays(1));
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }

    public void setSchedule(
            Instant saleStart, Instant saleEnd, Instant startTime, Instant endTime) {
        this.saleStart = saleStart;
        this.saleEnd = saleEnd;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public Organization getOrganization() {
        return organization;
    }

    public CommissionRule getCommissionRule() {
        return commissionRule;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public EventCategory getCategory() {
        return category;
    }

    public String getVenueName() {
        return venueName;
    }

    public String getVenueAddress() {
        return venueAddress;
    }

    public String getCoverImageUrl() {
        return eventImage;
    }

    public String getEventImage() {
        return eventImage;
    }

    public String getEventImagePublicId() {
        return eventImagePublicId;
    }

    public Instant getSaleStart() {
        return saleStart;
    }

    public Instant getSaleEnd() {
        return saleEnd;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public EventStatus getStatus() {
        return status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public List<Zone> getZones() {
        return List.copyOf(zones);
    }

    public boolean isSaleActive(Instant now) {
        return status == EventStatus.PUBLISHED && !now.isBefore(saleStart) && now.isBefore(saleEnd);
    }

    public boolean isCheckInOpen(Instant now) {
        return status == EventStatus.PUBLISHED
                && !now.isBefore(startTime.minusSeconds(3600))
                && now.isBefore(endTime);
    }
}
