package com.businessbuddy.entity;

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
import java.time.LocalDate;

/**
 * The result of evaluating a candidate model against the moving-average baseline on a
 * held-out test period that is always later than the training period (Table 4.33).
 */
@Entity
@Table(name = "model_evaluation")
@AttributeOverride(name = "id", column = @Column(name = "evaluation_id"))
public class ModelEvaluation extends BusinessOwnedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "forecast_type", nullable = false, length = 12)
    private ForecastType forecastType;

    @Column(name = "model_name", nullable = false, length = 60)
    private String modelName;

    @Column(name = "model_version", nullable = false, length = 20)
    private String modelVersion;

    @Column(name = "train_start", nullable = false)
    private LocalDate trainStart;

    @Column(name = "train_end", nullable = false)
    private LocalDate trainEnd;

    @Column(name = "test_start", nullable = false)
    private LocalDate testStart;

    @Column(name = "test_end", nullable = false)
    private LocalDate testEnd;

    @Column(name = "sample_days", nullable = false)
    private int sampleDays;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal mae;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal rmse;

    @Column(name = "baseline_mae", nullable = false, precision = 14, scale = 4)
    private BigDecimal baselineMae;

    @Column(name = "baseline_rmse", nullable = false, precision = 14, scale = 4)
    private BigDecimal baselineRmse;

    @Column(name = "selected_model", nullable = false, length = 60)
    private String selectedModel;

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public ForecastType getForecastType() { return forecastType; }
    public void setForecastType(ForecastType forecastType) { this.forecastType = forecastType; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public LocalDate getTrainStart() { return trainStart; }
    public void setTrainStart(LocalDate trainStart) { this.trainStart = trainStart; }
    public LocalDate getTrainEnd() { return trainEnd; }
    public void setTrainEnd(LocalDate trainEnd) { this.trainEnd = trainEnd; }
    public LocalDate getTestStart() { return testStart; }
    public void setTestStart(LocalDate testStart) { this.testStart = testStart; }
    public LocalDate getTestEnd() { return testEnd; }
    public void setTestEnd(LocalDate testEnd) { this.testEnd = testEnd; }
    public int getSampleDays() { return sampleDays; }
    public void setSampleDays(int sampleDays) { this.sampleDays = sampleDays; }
    public BigDecimal getMae() { return mae; }
    public void setMae(BigDecimal mae) { this.mae = mae; }
    public BigDecimal getRmse() { return rmse; }
    public void setRmse(BigDecimal rmse) { this.rmse = rmse; }
    public BigDecimal getBaselineMae() { return baselineMae; }
    public void setBaselineMae(BigDecimal baselineMae) { this.baselineMae = baselineMae; }
    public BigDecimal getBaselineRmse() { return baselineRmse; }
    public void setBaselineRmse(BigDecimal baselineRmse) { this.baselineRmse = baselineRmse; }
    public String getSelectedModel() { return selectedModel; }
    public void setSelectedModel(String selectedModel) { this.selectedModel = selectedModel; }
}
