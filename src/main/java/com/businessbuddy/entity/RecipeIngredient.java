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
 * One ingredient line of a recipe version (Table 4.21). Resolves the many-to-many
 * relationship between recipes and ingredients and carries the quantity, unit and
 * waste percentage, which belong to neither side alone.
 */
@Entity
@Table(name = "recipe_ingredient")
@AttributeOverride(name = "id", column = @Column(name = "recipe_ingredient_id"))
public class RecipeIngredient extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_version_id", nullable = false, updatable = false)
    private RecipeVersion recipeVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "unit_code", nullable = false)
    private UnitOfMeasure unit;

    /** Waste allowance as a decimal; applied once, on this line only (decision D7). */
    @Column(name = "waste_percent", nullable = false, precision = 5, scale = 4)
    private BigDecimal wastePercent = BigDecimal.ZERO;

    @Column(length = 300)
    private String notes;

    public RecipeVersion getRecipeVersion() { return recipeVersion; }
    public void setRecipeVersion(RecipeVersion recipeVersion) { this.recipeVersion = recipeVersion; }
    public Ingredient getIngredient() { return ingredient; }
    public void setIngredient(Ingredient ingredient) { this.ingredient = ingredient; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public UnitOfMeasure getUnit() { return unit; }
    public void setUnit(UnitOfMeasure unit) { this.unit = unit; }
    public BigDecimal getWastePercent() { return wastePercent; }
    public void setWastePercent(BigDecimal wastePercent) { this.wastePercent = wastePercent; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
