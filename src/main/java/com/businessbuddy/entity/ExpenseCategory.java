package com.businessbuddy.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** An operating expense category such as Rent or Electricity (Table 4.30). */
@Entity
@Table(name = "expense_category")
@AttributeOverride(name = "id", column = @Column(name = "expense_category_id"))
public class ExpenseCategory extends BusinessOwnedEntity {

    @Column(nullable = false, length = 60)
    private String name;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
