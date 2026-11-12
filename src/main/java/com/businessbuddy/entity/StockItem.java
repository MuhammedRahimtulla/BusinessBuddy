package com.businessbuddy.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Common view of anything held in stock: an {@link Ingredient} or a {@link Product}.
 *
 * <p>InventoryService works through this interface so the ledger rules (signed movements,
 * weighted-average cost, low-stock checks) are written once for both kinds of item.</p>
 */
public interface StockItem {

    Long getId();

    Business getBusiness();

    String getName();

    UnitOfMeasure getStockUnit();

    BigDecimal getCurrentQuantity();

    void setCurrentQuantity(BigDecimal quantity);

    BigDecimal getAverageUnitCost();

    void setAverageUnitCost(BigDecimal cost);

    BigDecimal getMinStockLevel();

    boolean isActive();

    /** "INGREDIENT" or "PRODUCT"; used for notifications and audit entries. */
    String stockItemType();

    default boolean isBelowMinimum() {
        return getCurrentQuantity().compareTo(getMinStockLevel()) < 0;
    }

    /** Current stock value at weighted-average cost, rounded to cents for display. */
    default BigDecimal stockValue() {
        BigDecimal qty = getCurrentQuantity().max(BigDecimal.ZERO);
        return qty.multiply(getAverageUnitCost()).setScale(2, RoundingMode.HALF_UP);
    }
}
