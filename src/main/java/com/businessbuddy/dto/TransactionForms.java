package com.businessbuddy.dto;

import com.businessbuddy.entity.Enums.MovementType;
import com.businessbuddy.entity.Enums.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Form objects for business transactions: purchases, production batches, sales, expenses and
 * manual stock changes (Tables 4.42 and 4.44 to 4.47).
 */
public final class TransactionForms {

    private TransactionForms() {
    }

    public static class PurchaseForm {
        @NotNull(message = "Choose a supplier") private Long supplierId;
        @NotBlank @Size(max = 50) private String referenceNo;
        @NotNull private LocalDate purchaseDate = LocalDate.now();
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) private BigDecimal discountAmount = BigDecimal.ZERO;
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) private BigDecimal additionalCosts = BigDecimal.ZERO;
        @Size(max = 2000) private String notes;
        @Valid private List<PurchaseLineForm> lines = new ArrayList<>();

        public Long getSupplierId() { return supplierId; }
        public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
        public String getReferenceNo() { return referenceNo; }
        public void setReferenceNo(String referenceNo) { this.referenceNo = referenceNo; }
        public LocalDate getPurchaseDate() { return purchaseDate; }
        public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }
        public BigDecimal getDiscountAmount() { return discountAmount; }
        public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
        public BigDecimal getAdditionalCosts() { return additionalCosts; }
        public void setAdditionalCosts(BigDecimal additionalCosts) { this.additionalCosts = additionalCosts; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
        public List<PurchaseLineForm> getLines() { return lines; }
        public void setLines(List<PurchaseLineForm> lines) { this.lines = lines; }
    }

    /**
     * One purchase line. {@code itemKey} is "I:&lt;id&gt;" for an ingredient or "P:&lt;id&gt;" for a
     * resale product, so one dropdown can list both. Blank rows are ignored.
     */
    public static class PurchaseLineForm {
        private String itemKey;
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 3) private BigDecimal quantity;
        private String unitCode;
        @DecimalMin("0") @Digits(integer = 10, fraction = 4) private BigDecimal unitCost;
        private LocalDate expiryDate;

        public boolean isBlank() { return itemKey == null || itemKey.isBlank(); }

        public String getItemKey() { return itemKey; }
        public void setItemKey(String itemKey) { this.itemKey = itemKey; }
        public BigDecimal getQuantity() { return quantity; }
        public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
        public String getUnitCode() { return unitCode; }
        public void setUnitCode(String unitCode) { this.unitCode = unitCode; }
        public BigDecimal getUnitCost() { return unitCost; }
        public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
        public LocalDate getExpiryDate() { return expiryDate; }
        public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    }

    public static class ProductionForm {
        @NotNull(message = "Choose a product") private Long productId;
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 3) private BigDecimal plannedOutput;
        @NotNull private LocalDate productionDate = LocalDate.now();
        @Size(max = 300) private String wasteNotes;

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public BigDecimal getPlannedOutput() { return plannedOutput; }
        public void setPlannedOutput(BigDecimal plannedOutput) { this.plannedOutput = plannedOutput; }
        public LocalDate getProductionDate() { return productionDate; }
        public void setProductionDate(LocalDate productionDate) { this.productionDate = productionDate; }
        public String getWasteNotes() { return wasteNotes; }
        public void setWasteNotes(String wasteNotes) { this.wasteNotes = wasteNotes; }
    }

    /** Confirmation of a batch: actual output and, optionally, actual quantities used. */
    public static class ConfirmBatchForm {
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 3) private BigDecimal actualOutput;
        @Size(max = 300) private String wasteNotes;
        private List<UsedLine> used = new ArrayList<>();

        public BigDecimal getActualOutput() { return actualOutput; }
        public void setActualOutput(BigDecimal actualOutput) { this.actualOutput = actualOutput; }
        public String getWasteNotes() { return wasteNotes; }
        public void setWasteNotes(String wasteNotes) { this.wasteNotes = wasteNotes; }
        public List<UsedLine> getUsed() { return used; }
        public void setUsed(List<UsedLine> used) { this.used = used; }
    }

    public static class UsedLine {
        private Long ingredientId;
        @DecimalMin(value = "0", inclusive = false) private BigDecimal quantity;

        public UsedLine() {
        }

        public UsedLine(Long ingredientId, BigDecimal quantity) {
            this.ingredientId = ingredientId;
            this.quantity = quantity;
        }

        public Long getIngredientId() { return ingredientId; }
        public void setIngredientId(Long ingredientId) { this.ingredientId = ingredientId; }
        public BigDecimal getQuantity() { return quantity; }
        public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    }

    public static class SaleForm {
        private Long customerId;
        @Size(max = 500) private String notes;
        private PaymentMethod paymentMethod;
        @Valid private List<SaleLineForm> lines = new ArrayList<>();

        public Long getCustomerId() { return customerId; }
        public void setCustomerId(Long customerId) { this.customerId = customerId; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
        public PaymentMethod getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }
        public List<SaleLineForm> getLines() { return lines; }
        public void setLines(List<SaleLineForm> lines) { this.lines = lines; }
    }

    public static class SaleLineForm {
        private Long productId;
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 3) private BigDecimal quantity;
        @DecimalMin("0") @Digits(integer = 10, fraction = 2) private BigDecimal unitPrice;
        @DecimalMin("0") @Digits(integer = 10, fraction = 2) private BigDecimal discountAmount = BigDecimal.ZERO;

        public SaleLineForm() {
        }

        public SaleLineForm(Long productId, BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountAmount) {
            this.productId = productId;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.discountAmount = discountAmount;
        }

        public boolean isBlank() { return productId == null; }

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public BigDecimal getQuantity() { return quantity; }
        public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
        public BigDecimal getDiscountAmount() { return discountAmount; }
        public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
    }

    public static class ExpenseForm {
        @NotNull(message = "Choose a category") private Long categoryId;
        @NotBlank @Size(max = 200) private String description;
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 10, fraction = 2) private BigDecimal amount;
        @NotNull private LocalDate expenseDate = LocalDate.now();
        private Long supplierId;
        private PaymentMethod paymentMethod;
        @Size(max = 50) private String referenceNo;
        @Size(max = 2000) private String notes;

        public Long getCategoryId() { return categoryId; }
        public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public LocalDate getExpenseDate() { return expenseDate; }
        public void setExpenseDate(LocalDate expenseDate) { this.expenseDate = expenseDate; }
        public Long getSupplierId() { return supplierId; }
        public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
        public PaymentMethod getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }
        public String getReferenceNo() { return referenceNo; }
        public void setReferenceNo(String referenceNo) { this.referenceNo = referenceNo; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    public static class StockChangeForm {
        @NotBlank private String itemKey;
        @NotNull private MovementType type = MovementType.WASTE;
        @NotNull @Digits(integer = 11, fraction = 3) private BigDecimal quantity;
        @DecimalMin("0") @Digits(integer = 10, fraction = 4) private BigDecimal unitCost;
        @NotBlank @Size(min = 3, max = 300) private String reason;

        public String getItemKey() { return itemKey; }
        public void setItemKey(String itemKey) { this.itemKey = itemKey; }
        public MovementType getType() { return type; }
        public void setType(MovementType type) { this.type = type; }
        public BigDecimal getQuantity() { return quantity; }
        public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
        public BigDecimal getUnitCost() { return unitCost; }
        public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
