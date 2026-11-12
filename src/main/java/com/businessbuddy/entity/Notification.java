package com.businessbuddy.entity;

import com.businessbuddy.entity.Enums.NotificationNature;
import com.businessbuddy.entity.Enums.NotificationStatus;
import com.businessbuddy.entity.Enums.NotificationType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * An in-app alert (Table 4.34). {@code nature} records whether the alert describes an
 * observed condition or a prediction. A partial unique index stops duplicate open alerts.
 */
@Entity
@Table(name = "notification")
@AttributeOverride(name = "id", column = @Column(name = "notification_id"))
public class Notification extends BusinessOwnedEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 30)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NotificationNature nature;

    @Column(nullable = false, length = 300)
    private String message;

    @Column(name = "related_type", length = 20)
    private String relatedType;

    @Column(name = "related_id")
    private Long relatedId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NotificationStatus status = NotificationStatus.OPEN;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public NotificationType getType() { return type; }
    public void setType(NotificationType type) { this.type = type; }
    public NotificationNature getNature() { return nature; }
    public void setNature(NotificationNature nature) { this.nature = nature; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getRelatedType() { return relatedType; }
    public void setRelatedType(String relatedType) { this.relatedType = relatedType; }
    public Long getRelatedId() { return relatedId; }
    public void setRelatedId(Long relatedId) { this.relatedId = relatedId; }
    public NotificationStatus getStatus() { return status; }
    public void setStatus(NotificationStatus status) { this.status = status; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
}
