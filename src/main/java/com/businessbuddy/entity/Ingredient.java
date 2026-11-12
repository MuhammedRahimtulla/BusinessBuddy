package com.businessbuddy.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * An ingredient or raw material (Table 4.18).
 *
 * <p>{@code currentQuantity} (in the base unit) and {@code averageUnitCost} (per base unit)
 * are a cached view of the inventory ledger. Only InventoryService changes them, in the
 * same transaction as the movement that explains the change (Table 4.36).</p>
 */
@Entity
@Table(name = "ingredient")
@AttributeOverride(name = "id", column = @Column(name = "ingredient_id"))
public class Ingredient extends BusinessOwnedEntity implements StockItem {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_supplier_id")
    private Supplier preferredSupplier;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "base_unit_code", nullable = false)
    private UnitOfMeasure baseUnit;

    @Column(name = "current_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal currentQuantity = BigDecimal.ZERO;

    @Column(name = "average_unit_cost", nullable = false, precision = 14, scale = 4)
    private BigDecimal averageUnitCost = BigDecimal.ZERO;

    @Column(name = "min_stock_level", nullable = false, precision = 14, scale = 3)
    private BigDecimal minStockLevel = BigDecimal.ZERO;

    @Column(name = "reorder_quantity", precision = 14, scale = 3)
    private BigDecimal reorderQuantity;

    @Column(name = "expiry_tracked", nullable = false)
    private boolean expiryTracked;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Override
    public UnitOfMeasure getStockUnit() { return baseUnit; }

    @Override
    public String stockItemType() { return "INGREDIENT"; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public Supplier getPreferredSupplier() { return preferredSupplier; }
    public void setPreferredSupplier(Supplier preferredSupplier) { this.preferredSupplier = preferredSupplier; }
    @Override public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public UnitOfMeasure getBaseUnit() { return baseUnit; }
    public void setBaseUnit(UnitOfMeasure baseUnit) { this.baseUnit = baseUnit; }
    @Override public BigDecimal getCurrentQuantity() { return currentQuantity; }
    @Override public void setCurrentQuantity(BigDecimal currentQuantity) { this.currentQuantity = currentQuantity; }
    @Override public BigDecimal getAverageUnitCost() { return averageUnitCost; }
    @Override public void setAverageUnitCost(BigDecimal averageUnitCost) { this.averageUnitCost = averageUnitCost; }
    @Override public BigDecimal getMinStockLevel() { return minStockLevel; }
    public void setMinStockLevel(BigDecimal minStockLevel) { this.minStockLevel = minStockLevel; }
    public BigDecimal getReorderQuantity() { return reorderQuantity; }
    public void setReorderQuantity(BigDecimal reorderQuantity) { this.reorderQuantity = reorderQuantity; }
    public boolean isExpiryTracked() { return expiryTracked; }
    public void setExpiryTracked(boolean expiryTracked) { this.expiryTracked = expiryTracked; }
    @Override public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
