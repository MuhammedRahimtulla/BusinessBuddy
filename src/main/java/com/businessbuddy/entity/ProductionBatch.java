package com.businessbuddy.entity;

import com.businessbuddy.entity.Enums.BatchStatus;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * A production run of a manufactured product (Table 4.25). Confirming the batch consumes
 * the ingredients and adds finished stock (4.6.7). Ingredients are never consumed again
 * when the finished product is sold.
 */
@Entity
@Table(name = "production_batch")
@AttributeOverride(name = "id", column = @Column(name = "batch_id"))
public class ProductionBatch extends BusinessOwnedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_version_id", nullable = false)
    private RecipeVersion recipeVersion;

    @Column(name = "planned_output", nullable = false, precision = 14, scale = 3)
    private BigDecimal plannedOutput;

    @Column(name = "actual_output", precision = 14, scale = 3)
    private BigDecimal actualOutput;

    @Column(name = "production_date", nullable = false)
    private LocalDate productionDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BatchStatus status = BatchStatus.DRAFT;

    @Column(name = "total_cost", precision = 12, scale = 2)
    private BigDecimal totalCost;

    @Column(name = "unit_cost", precision = 14, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "waste_notes", length = 300)
    private String wasteNotes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produced_by", nullable = false)
    private User producedBy;

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ProductionConsumption> consumptions = new ArrayList<>();

    public void addConsumption(ProductionConsumption consumption) {
        consumption.setBatch(this);
        consumptions.add(consumption);
    }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public RecipeVersion getRecipeVersion() { return recipeVersion; }
    public void setRecipeVersion(RecipeVersion recipeVersion) { this.recipeVersion = recipeVersion; }
    public BigDecimal getPlannedOutput() { return plannedOutput; }
    public void setPlannedOutput(BigDecimal plannedOutput) { this.plannedOutput = plannedOutput; }
    public BigDecimal getActualOutput() { return actualOutput; }
    public void setActualOutput(BigDecimal actualOutput) { this.actualOutput = actualOutput; }
    public LocalDate getProductionDate() { return productionDate; }
    public void setProductionDate(LocalDate productionDate) { this.productionDate = productionDate; }
    public BatchStatus getStatus() { return status; }
    public void setStatus(BatchStatus status) { this.status = status; }
    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
    public String getWasteNotes() { return wasteNotes; }
    public void setWasteNotes(String wasteNotes) { this.wasteNotes = wasteNotes; }
    public User getProducedBy() { return producedBy; }
    public void setProducedBy(User producedBy) { this.producedBy = producedBy; }
    public List<ProductionConsumption> getConsumptions() { return consumptions; }
}
