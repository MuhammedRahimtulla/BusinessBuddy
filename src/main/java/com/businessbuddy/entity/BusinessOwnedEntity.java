package com.businessbuddy.entity;

import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;

/**
 * Superclass of records that belong directly to one business (decision D1).
 *
 * <p>Keeping the business reference in one place lets {@code BusinessContext} check
 * ownership the same way for every record type.</p>
 */
@MappedSuperclass
public abstract class BusinessOwnedEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false, updatable = false)
    private Business business;

    public Business getBusiness() { return business; }
    public void setBusiness(Business business) { this.business = business; }

    /** Business identifier without forcing the lazy proxy to load. */
    public Long owningBusinessId() { return business == null ? null : business.getId(); }
}
