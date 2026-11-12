package com.businessbuddy.entity;

import com.businessbuddy.entity.Enums.MovementType;
import com.businessbuddy.entity.Enums.SourceType;
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
import java.time.Instant;

/**
 * One row of the inventory ledger (Table 4.24, decision D4).
 *
 * <p>Every stock change is one movement with a signed quantity in the item's stock unit.
 * Movements are never edited or deleted; a mistake is corrected with a REVERSAL or an
 * ADJUSTMENT, so the history stays complete.</p>
 */
@Entity
@Table(name = "inventory_movement")
@AttributeOverride(name = "id", column = @Column(name = "movement_id"))
public class InventoryMovement extends BusinessOwnedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", updatable = false)
    private Ingredient ingredient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", updatable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 25, updatable = false)
    private MovementType type;

    @Column(name = "quantity_change", nullable = false, precision = 14, scale = 3, updatable = false)
    private BigDecimal quantityChange;

    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 4, updatable = false)
    private BigDecimal unitCost;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30, updatable = false)
    private SourceType sourceType;

    @Column(name = "source_id", updatable = false)
    private Long sourceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversed_movement_id", updatable = false)
    private InventoryMovement reversedMovement;

    @Column(name = "movement_date", nullable = false, updatable = false)
    private Instant movementDate;

    @Column(length = 300, updatable = false)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    public StockItem stockItem() {
        return ingredient != null ? ingredient : product;
    }

    /** Value of the movement at its recorded unit cost (signed). */
    public BigDecimal value() {
        return quantityChange.multiply(unitCost);
    }

    public Ingredient getIngredient() { return ingredient; }
    public void setIngredient(Ingredient ingredient) { this.ingredient = ingredient; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public MovementType getType() { return type; }
    public void setType(MovementType type) { this.type = type; }
    public BigDecimal getQuantityChange() { return quantityChange; }
    public void setQuantityChange(BigDecimal quantityChange) { this.quantityChange = quantityChange; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
    public SourceType getSourceType() { return sourceType; }
    public void setSourceType(SourceType sourceType) { this.sourceType = sourceType; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public InventoryMovement getReversedMovement() { return reversedMovement; }
    public void setReversedMovement(InventoryMovement reversedMovement) { this.reversedMovement = reversedMovement; }
    public Instant getMovementDate() { return movementDate; }
    public void setMovementDate(Instant movementDate) { this.movementDate = movementDate; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
}
