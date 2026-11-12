package com.businessbuddy.dto;

import com.businessbuddy.entity.Enums.CategoryType;
import com.businessbuddy.entity.Enums.ProductType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Form objects for master data: categories, suppliers, ingredients, products, customers and
 * recipe versions. Validation rules follow the input design in Tables 4.40, 4.41 and 4.43.
 */
public final class CatalogForms {

    private CatalogForms() {
    }

    public static class CategoryForm {
        @NotBlank @Size(max = 80) private String name;
        @NotNull private CategoryType type = CategoryType.INGREDIENT;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public CategoryType getType() { return type; }
        public void setType(CategoryType type) { this.type = type; }
    }

    public static class SupplierForm {
        @NotBlank @Size(max = 150) private String name;
        @Size(max = 120) private String contactPerson;
        @Email @Size(max = 255) private String email;
        @Size(max = 30) @Pattern(regexp = "^[0-9+ ]*$", message = "Use digits, spaces and + only") private String phone;
        @Size(max = 300) private String address;
        @Size(max = 2000) private String notes;
        private boolean active = true;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getContactPerson() { return contactPerson; }
        public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
    }

    public static class IngredientForm {
        @NotBlank @Size(max = 120) private String name;
        private Long categoryId;
        @NotBlank private String baseUnitCode = "g";
        @NotNull @DecimalMin("0") @Digits(integer = 11, fraction = 3) private BigDecimal minStockLevel = BigDecimal.ZERO;
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 3) private BigDecimal reorderQuantity;
        private Long preferredSupplierId;
        private boolean expiryTracked;
        @Size(max = 500) private String description;
        private boolean active = true;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Long getCategoryId() { return categoryId; }
        public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
        public String getBaseUnitCode() { return baseUnitCode; }
        public void setBaseUnitCode(String baseUnitCode) { this.baseUnitCode = baseUnitCode; }
        public BigDecimal getMinStockLevel() { return minStockLevel; }
        public void setMinStockLevel(BigDecimal minStockLevel) { this.minStockLevel = minStockLevel; }
        public BigDecimal getReorderQuantity() { return reorderQuantity; }
        public void setReorderQuantity(BigDecimal reorderQuantity) { this.reorderQuantity = reorderQuantity; }
        public Long getPreferredSupplierId() { return preferredSupplierId; }
        public void setPreferredSupplierId(Long preferredSupplierId) { this.preferredSupplierId = preferredSupplierId; }
        public boolean isExpiryTracked() { return expiryTracked; }
        public void setExpiryTracked(boolean expiryTracked) { this.expiryTracked = expiryTracked; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
    }

    public static class ProductForm {
        @NotBlank @Size(max = 120) private String name;
        @Size(max = 40) private String sku;
        private Long categoryId;
        @NotNull private ProductType type = ProductType.MANUFACTURED;
        @NotBlank private String saleUnitCode = "unit";
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) private BigDecimal sellingPrice = BigDecimal.ZERO;
        @NotNull @DecimalMin("0") @Digits(integer = 11, fraction = 3) private BigDecimal minStockLevel = BigDecimal.ZERO;
        @Size(max = 500) private String description;
        private boolean active = true;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        public Long getCategoryId() { return categoryId; }
        public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
        public ProductType getType() { return type; }
        public void setType(ProductType type) { this.type = type; }
        public String getSaleUnitCode() { return saleUnitCode; }
        public void setSaleUnitCode(String saleUnitCode) { this.saleUnitCode = saleUnitCode; }
        public BigDecimal getSellingPrice() { return sellingPrice; }
        public void setSellingPrice(BigDecimal sellingPrice) { this.sellingPrice = sellingPrice; }
        public BigDecimal getMinStockLevel() { return minStockLevel; }
        public void setMinStockLevel(BigDecimal minStockLevel) { this.minStockLevel = minStockLevel; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
    }

    public static class CustomerForm {
        @NotBlank @Size(max = 120) private String name;
        @Email @Size(max = 255) private String email;
        @Size(max = 30) @Pattern(regexp = "^[0-9+ ]*$", message = "Use digits, spaces and + only") private String phone;
        @Size(max = 500) private String notes;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    public static class RecipeForm {
        @NotBlank @Size(max = 120) private String name;
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 3)
        private BigDecimal batchOutputQty;
        @NotBlank private String outputUnitCode = "unit";
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) private BigDecimal labourCost = BigDecimal.ZERO;
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) private BigDecimal packagingCost = BigDecimal.ZERO;
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) private BigDecimal utilitiesCost = BigDecimal.ZERO;
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) private BigDecimal overheadFixed = BigDecimal.ZERO;
        @NotNull @DecimalMin("0") @DecimalMax("100") private BigDecimal overheadPercent = BigDecimal.ZERO;
        @NotNull private LocalDate effectiveFrom = LocalDate.now();
        @Size(max = 4000) private String preparationNotes;
        private boolean makeActive = true;
        @Valid private List<RecipeLineForm> lines = new ArrayList<>();

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public BigDecimal getBatchOutputQty() { return batchOutputQty; }
        public void setBatchOutputQty(BigDecimal batchOutputQty) { this.batchOutputQty = batchOutputQty; }
        public String getOutputUnitCode() { return outputUnitCode; }
        public void setOutputUnitCode(String outputUnitCode) { this.outputUnitCode = outputUnitCode; }
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
        public LocalDate getEffectiveFrom() { return effectiveFrom; }
        public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
        public String getPreparationNotes() { return preparationNotes; }
        public void setPreparationNotes(String preparationNotes) { this.preparationNotes = preparationNotes; }
        public boolean isMakeActive() { return makeActive; }
        public void setMakeActive(boolean makeActive) { this.makeActive = makeActive; }
        public List<RecipeLineForm> getLines() { return lines; }
        public void setLines(List<RecipeLineForm> lines) { this.lines = lines; }
    }

    /** One recipe line. Rows with no ingredient selected are ignored (blank rows on the form). */
    public static class RecipeLineForm {
        private Long ingredientId;
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 3) private BigDecimal quantity;
        private String unitCode;
        @DecimalMin("0") @DecimalMax(value = "100", inclusive = false) private BigDecimal wastePercent = BigDecimal.ZERO;

        public boolean isBlank() { return ingredientId == null; }

        public Long getIngredientId() { return ingredientId; }
        public void setIngredientId(Long ingredientId) { this.ingredientId = ingredientId; }
        public BigDecimal getQuantity() { return quantity; }
        public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
        public String getUnitCode() { return unitCode; }
        public void setUnitCode(String unitCode) { this.unitCode = unitCode; }
        public BigDecimal getWastePercent() { return wastePercent; }
        public void setWastePercent(BigDecimal wastePercent) { this.wastePercent = wastePercent; }
    }
}
