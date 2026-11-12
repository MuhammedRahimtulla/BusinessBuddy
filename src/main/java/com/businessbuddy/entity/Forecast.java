package com.businessbuddy.entity;

import com.businessbuddy.entity.Enums.ForecastStatus;
import com.businessbuddy.entity.Enums.ForecastType;
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
import java.time.LocalDate;

/**
 * A stored demand forecast (units for a product on a date) or stockout estimate (days left
 * for an ingredient), Table 4.32. Always shown with an "Estimate" label in the interface.
 */
@Entity
@Table(name = "forecast")
@AttributeOverride(name = "id", column = @Column(name = "forecast_id"))
public class Forecast extends BusinessOwnedEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "forecast_type", nullable = false, length = 12)
    private ForecastType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id")
    private Ingredient ingredient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_evaluation_id")
    private ModelEvaluation modelEvaluation;

    @Column(name = "model_name", nullable = false, length = 60)
    private String modelName;

    @Column(name = "model_version", nullable = false, length = 20)
    private String modelVersion;

    @Column(name = "training_cutoff", nullable = false)
    private LocalDate trainingCutoff;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Column(name = "horizon_days", nullable = false)
    private int horizonDays;

    @Column(name = "predicted_value", precision = 14, scale = 3)
    private BigDecimal predictedValue;

    @Column(name = "value_unit", nullable = false, length = 10)
    private String valueUnit;

    @Column(name = "actual_value", precision = 14, scale = 3)
    private BigDecimal actualValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ForecastStatus status;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    public ForecastType getType() { return type; }
    public void setType(ForecastType type) { this.type = type; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public Ingredient getIngredient() { return ingredient; }
    public void setIngredient(Ingredient ingredient) { this.ingredient = ingredient; }
    public ModelEvaluation getModelEvaluation() { return modelEvaluation; }
    public void setModelEvaluation(ModelEvaluation modelEvaluation) { this.modelEvaluation = modelEvaluation; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public LocalDate getTrainingCutoff() { return trainingCutoff; }
    public void setTrainingCutoff(LocalDate trainingCutoff) { this.trainingCutoff = trainingCutoff; }
    public LocalDate getTargetDate() { return targetDate; }
    public void setTargetDate(LocalDate targetDate) { this.targetDate = targetDate; }
    public int getHorizonDays() { return horizonDays; }
    public void setHorizonDays(int horizonDays) { this.horizonDays = horizonDays; }
    public BigDecimal getPredictedValue() { return predictedValue; }
    public void setPredictedValue(BigDecimal predictedValue) { this.predictedValue = predictedValue; }
    public String getValueUnit() { return valueUnit; }
    public void setValueUnit(String valueUnit) { this.valueUnit = valueUnit; }
    public BigDecimal getActualValue() { return actualValue; }
    public void setActualValue(BigDecimal actualValue) { this.actualValue = actualValue; }
    public ForecastStatus getStatus() { return status; }
    public void setStatus(ForecastStatus status) { this.status = status; }
    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
}
