package com.businessbuddy.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One line of a purchase (Table 4.23). A line buys either an ingredient or a resale
 * product, never both (ck_purchase_item_target). The purchase-time unit cost is kept
 * forever, which is how ingredient price history is reported.
 */
@Entity
@Table(name = "purchase_item")
@AttributeOverride(name = "id", column = @Column(name = "purchase_item_id"))
public class PurchaseItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_id", nullable = false, updatable = false)
    private Purchase purchase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id")
    private Ingredient ingredient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "unit_code", nullable = false)
    private UnitOfMeasure unit;

    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    /** Quantity converted to the item's stock unit; set when the purchase is received. */
    @Column(name = "base_quantity", precision = 14, scale = 3)
    private BigDecimal baseQuantity;

    /** Cost per stock unit after sharing discounts and additional costs; set on receipt. */
    @Column(name = "landed_unit_cost", precision = 14, scale = 4)
    private BigDecimal landedUnitCost;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /** The ingredient or product bought on this line. */
    public StockItem stockItem() {
        return ingredient != null ? ingredient : product;
    }

    public Purchase getPurchase() { return purchase; }
    public void setPurchase(Purchase purchase) { this.purchase = purchase; }
    public Ingredient getIngredient() { return ingredient; }
    public void setIngredient(Ingredient ingredient) { this.ingredient = ingredient; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public UnitOfMeasure getUnit() { return unit; }
    public void setUnit(UnitOfMeasure unit) { this.unit = unit; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }
    public BigDecimal getBaseQuantity() { return baseQuantity; }
    public void setBaseQuantity(BigDecimal baseQuantity) { this.baseQuantity = baseQuantity; }
    public BigDecimal getLandedUnitCost() { return landedUnitCost; }
    public void setLandedUnitCost(BigDecimal landedUnitCost) { this.landedUnitCost = landedUnitCost; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
}
