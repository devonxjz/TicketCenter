package vn.ticketscenter.audit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import vn.ticketscenter.identity.model.User;
import vn.ticketscenter.persistence.model.BaseEntity;

@Entity
@Table(name = "tc_audit_logs", schema = "dbo")
public class AuditLog extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id", length = 100)
    private String entityId;

    @Column(name = "metadata_json", length = 8000)
    private String metadataJson;

    protected AuditLog() { }

    public AuditLog(User actor, String action, String entityType, String entityId, String metadataJson) {
        this.actor = actor;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.metadataJson = metadataJson;
    }

    public User getActor() { return actor; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public String getMetadataJson() { return metadataJson; }
}
