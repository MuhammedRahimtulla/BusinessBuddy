package com.businessbuddy.service.costing;

import java.math.BigDecimal;
import java.util.List;

/**
 * Result of a recipe cost calculation (Figure 4.6). Immutable, so it can be shown, tested
 * and compared without side effects. Amounts keep 4 decimals; screens round to 2.
 */
public record CostBreakdown(
        List<LineCost> lines,
        BigDecimal ingredientCost,
        BigDecimal labourCost,
        BigDecimal packagingCost,
        BigDecimal utilitiesCost,
        BigDecimal overheadCost,
        BigDecimal batchCost,
        BigDecimal outputQuantity,
        BigDecimal unitCost) {

    /** Cost of one recipe line: base quantity incl. waste multiplied by the average cost. */
    public record LineCost(
            Long ingredientId,
            String ingredientName,
            BigDecimal baseQuantity,
            BigDecimal wastePercent,
            BigDecimal quantityWithWaste,
            BigDecimal averageUnitCost,
            BigDecimal lineCost) {
    }
}
