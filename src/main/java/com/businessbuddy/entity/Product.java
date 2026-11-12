package com.businessbuddy.entity;

import com.businessbuddy.entity.Enums.ProductType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * A product sold by the business (Table 4.19). MANUFACTURED products are made from a recipe
 * through production batches; RESALE products are bought in and sold without production.
 * Finished stock is tracked for both (decision D2).
 */
@Entity
@Table(name = "product")
@AttributeOverride(name = "id", column = @Column(name = "product_id"))
public class Product extends BusinessOwnedEntity implements StockItem {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 40)
    private String sku;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 12)
    private ProductType type;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "sale_unit_code", nullable = false)
    private UnitOfMeasure saleUnit;

    /** Current list price, excluding tax (decision D5). */
    @Column(name = "selling_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal sellingPrice = BigDecimal.ZERO;

    @Column(name = "current_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal currentQuantity = BigDecimal.ZERO;

    @Column(name = "average_unit_cost", nullable = false, precision = 14, scale = 4)
    private BigDecimal averageUnitCost = BigDecimal.ZERO;

    @Column(name = "min_stock_level", nullable = false, precision = 14, scale = 3)
    private BigDecimal minStockLevel = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Override
    public UnitOfMeasure getStockUnit() { return saleUnit; }

    @Override
    public String stockItemType() { return "PRODUCT"; }

    public boolean isManufactured() { return type == ProductType.MANUFACTURED; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    @Override public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public ProductType getType() { return type; }
    public void setType(ProductType type) { this.type = type; }
    public UnitOfMeasure getSaleUnit() { return saleUnit; }
    public void setSaleUnit(UnitOfMeasure saleUnit) { this.saleUnit = saleUnit; }
    public BigDecimal getSellingPrice() { return sellingPrice; }
    public void setSellingPrice(BigDecimal sellingPrice) { this.sellingPrice = sellingPrice; }
    @Override public BigDecimal getCurrentQuantity() { return currentQuantity; }
    @Override public void setCurrentQuantity(BigDecimal currentQuantity) { this.currentQuantity = currentQuantity; }
    @Override public BigDecimal getAverageUnitCost() { return averageUnitCost; }
    @Override public void setAverageUnitCost(BigDecimal averageUnitCost) { this.averageUnitCost = averageUnitCost; }
    @Override public BigDecimal getMinStockLevel() { return minStockLevel; }
    public void setMinStockLevel(BigDecimal minStockLevel) { this.minStockLevel = minStockLevel; }
    @Override public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
