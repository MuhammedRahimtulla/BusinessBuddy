package com.businessbuddy.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** An optional customer record (Table 4.27). */
@Entity
@Table(name = "customer")
@AttributeOverride(name = "id", column = @Column(name = "customer_id"))
public class Customer extends BusinessOwnedEntity {

    @Column(nullable = false, length = 120)
    private String name;

    private String email;

    @Column(length = 30)
    private String phone;

    @Column(length = 500)
    private String notes;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
