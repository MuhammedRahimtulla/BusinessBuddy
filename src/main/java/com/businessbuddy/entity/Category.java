package com.businessbuddy.entity;

import com.businessbuddy.entity.Enums.CategoryType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** A product or ingredient category (Table 4.16). */
@Entity
@Table(name = "category")
@AttributeOverride(name = "id", column = @Column(name = "category_id"))
public class Category extends BusinessOwnedEntity {

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_type", nullable = false, length = 12)
    private CategoryType type;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public CategoryType getType() { return type; }
    public void setType(CategoryType type) { this.type = type; }
}
