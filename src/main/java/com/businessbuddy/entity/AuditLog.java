package com.businessbuddy.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An append-only record of an important action (Table 4.14). The entity is immutable in
 * Hibernate, and in production the database user has INSERT and SELECT rights only (4.8.5).
 */
@Entity
@Immutable
@Table(name = "audit_log")
@AttributeOverride(name = "id", column = @Column(name = "audit_id"))
public class AuditLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id")
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(name = "entity_type", length = 40)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    /** JSON object with changed values; never contains passwords. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String details;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    public Business getBusiness() { return business; }
    public void setBusiness(Business business) { this.business = business; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
}
