package com.businessbuddy.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** A supplier of a business (Table 4.17). Suppliers are deactivated, never deleted. */
@Entity
@Table(name = "supplier")
@AttributeOverride(name = "id", column = @Column(name = "supplier_id"))
public class Supplier extends BusinessOwnedEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "contact_person", length = 120)
    private String contactPerson;

    private String email;
    private String phone;
    private String address;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
