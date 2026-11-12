package com.businessbuddy.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * One version of a product's recipe (bill of materials), Table 4.20.
 *
 * <p>Recipes are versioned: editing a recipe creates a new version so historical batches
 * keep pointing at the version they actually followed (8.9, 8.12). Only one version per
 * product is active (partial unique index uq_recipe_active).</p>
 */
@Entity
@Table(name = "recipe_version")
@AttributeOverride(name = "id", column = @Column(name = "recipe_version_id"))
public class RecipeVersion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "batch_output_qty", nullable = false, precision = 14, scale = 3)
    private BigDecimal batchOutputQty;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "output_unit_code", nullable = false)
    private UnitOfMeasure outputUnit;

    @Column(name = "labour_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal labourCost = BigDecimal.ZERO;

    @Column(name = "packaging_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal packagingCost = BigDecimal.ZERO;

    @Column(name = "utilities_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal utilitiesCost = BigDecimal.ZERO;

    @Column(name = "overhead_fixed", nullable = false, precision = 12, scale = 2)
    private BigDecimal overheadFixed = BigDecimal.ZERO;

    /** Percentage of ingredient cost, stored as a decimal (0.1000 = 10%), decision D6. */
    @Column(name = "overhead_percent", nullable = false, precision = 5, scale = 4)
    private BigDecimal overheadPercent = BigDecimal.ZERO;

    @Column(name = "preparation_notes", columnDefinition = "text")
    private String preparationNotes;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    @OneToMany(mappedBy = "recipeVersion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<RecipeIngredient> ingredients = new ArrayList<>();

    public void addIngredient(RecipeIngredient line) {
        line.setRecipeVersion(this);
        ingredients.add(line);
    }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public int getVersionNo() { return versionNo; }
    public void setVersionNo(int versionNo) { this.versionNo = versionNo; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getBatchOutputQty() { return batchOutputQty; }
    public void setBatchOutputQty(BigDecimal batchOutputQty) { this.batchOutputQty = batchOutputQty; }
    public UnitOfMeasure getOutputUnit() { return outputUnit; }
    public void setOutputUnit(UnitOfMeasure outputUnit) { this.outputUnit = outputUnit; }
    public BigDecimal getLabourCost() { return labourCost; }
    public void setLabourCost(BigDecimal labourCost) { this.labourCost = labourCost; }
    public BigDecimal getPackagingCost() { return packagingCost; }
    public void setPackagingCost(BigDecimal packagingCost) { this.packagingCost = packagingCost; }
    public BigDecimal getUtilitiesCost() { return utilitiesCost; }
    public void setUtilitiesCost(BigDecimal utilitiesCost) { this.utilitiesCost = utilitiesCost; }
    public BigDecimal getOverheadFixed() { return overheadFixed; }
    public void setOverheadFixed(BigDecimal overheadFixed) { this.overheadFixed = overheadFixed; }
    public BigDecimal getOverheadPercent() { return overheadPercent; }
    public void setOverheadPercent(BigDecimal overheadPercent) { this.overheadPercent = overheadPercent; }
    public String getPreparationNotes() { return preparationNotes; }
    public void setPreparationNotes(String preparationNotes) { this.preparationNotes = preparationNotes; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public List<RecipeIngredient> getIngredients() { return ingredients; }
}
