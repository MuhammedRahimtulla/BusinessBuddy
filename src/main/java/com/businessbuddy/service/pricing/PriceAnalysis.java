package com.businessbuddy.service.pricing;

import java.math.BigDecimal;

/**
 * Result of analysing a selling price against a unit cost (Figure 4.6).
 *
 * <p>{@code margin} = profit / price and {@code markup} = profit / cost, both as decimals
 * (0.25 = 25%). Either is null when it is mathematically undefined (price or cost of zero).
 * The break-even price under this cost model equals the unit cost; it does not cover
 * operating expenses (spec 8.13).</p>
 */
public record PriceAnalysis(
        BigDecimal unitCost,
        BigDecimal priceExcludingTax,
        BigDecimal profitPerUnit,
        BigDecimal margin,
        BigDecimal markup,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        BigDecimal priceIncludingTax,
        BigDecimal breakEvenPrice,
        boolean belowCost) {
}
