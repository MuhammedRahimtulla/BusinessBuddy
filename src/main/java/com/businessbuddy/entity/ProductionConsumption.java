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
 * An ingredient actually consumed by a confirmed batch, with its cost snapshot (Table 4.26).
 * Written once at confirmation and never updated.
 */
@Entity
@Table(name = "production_consumption")
@AttributeOverride(name = "id", column = @Column(name = "consumption_id"))
public class ProductionConsumption extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false, updatable = false)
    private ProductionBatch batch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false, updatable = false)
    private Ingredient ingredient;

    @Column(name = "quantity_base", nullable = false, precision = 14, scale = 3, updatable = false)
    private BigDecimal quantityBase;

    @Column(name = "unit_cost_snapshot", nullable = false, precision = 14, scale = 4, updatable = false)
    private BigDecimal unitCostSnapshot;

    @Column(name = "line_cost", nullable = false, precision = 14, scale = 4, updatable = false)
    private BigDecimal lineCost;

    public ProductionBatch getBatch() { return batch; }
    public void setBatch(ProductionBatch batch) { this.batch = batch; }
    public Ingredient getIngredient() { return ingredient; }
    public void setIngredient(Ingredient ingredient) { this.ingredient = ingredient; }
    public BigDecimal getQuantityBase() { return quantityBase; }
    public void setQuantityBase(BigDecimal quantityBase) { this.quantityBase = quantityBase; }
    public BigDecimal getUnitCostSnapshot() { return unitCostSnapshot; }
    public void setUnitCostSnapshot(BigDecimal unitCostSnapshot) { this.unitCostSnapshot = unitCostSnapshot; }
    public BigDecimal getLineCost() { return lineCost; }
    public void setLineCost(BigDecimal lineCost) { this.lineCost = lineCost; }
}
