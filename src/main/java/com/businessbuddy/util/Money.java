package com.businessbuddy.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money and quantity helpers implementing the rounding policy of decision D15.
 *
 * <ul>
 *   <li>Calculations keep full precision (scale 10 for divisions).</li>
 *   <li>Money totals are rounded HALF_UP to 2 decimals only when stored or displayed.</li>
 *   <li>Unit costs keep 4 decimals; quantities keep 3 decimals.</li>
 * </ul>
 * Floating-point types are never used for money.
 */
public final class Money {

    public static final int CALC_SCALE = 10;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal HUNDRED = new BigDecimal("100");

    private Money() {
    }

    public static BigDecimal money(BigDecimal value) {
        return value.setScale(2, ROUNDING);
    }

    public static BigDecimal unitCost(BigDecimal value) {
        return value.setScale(4, ROUNDING);
    }

    public static BigDecimal quantity(BigDecimal value) {
        return value.setScale(3, ROUNDING);
    }

    public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor) {
        return dividend.divide(divisor, CALC_SCALE, ROUNDING);
    }

    public static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** 15 (percent, as typed by a user) becomes 0.1500. */
    public static BigDecimal percentToRate(BigDecimal percent) {
        return percent == null ? BigDecimal.ZERO : percent.divide(HUNDRED, 4, ROUNDING);
    }

    /** 0.1500 becomes 15.00 for display in a form. */
    public static BigDecimal rateToPercent(BigDecimal rate) {
        return rate == null ? BigDecimal.ZERO : rate.multiply(HUNDRED).setScale(2, ROUNDING);
    }

    /**
     * Weighted-average cost after adding {@code addedQty} at {@code addedCost} to
     * {@code oldQty} held at {@code oldCost} (decision D3). Negative stock counts as zero.
     */
    public static BigDecimal weightedAverage(BigDecimal oldQty, BigDecimal oldCost,
                                             BigDecimal addedQty, BigDecimal addedCost) {
        BigDecimal held = oldQty.max(BigDecimal.ZERO);
        BigDecimal totalQty = held.add(addedQty);
        if (totalQty.signum() <= 0) {
            return unitCost(addedCost);
        }
        BigDecimal totalValue = held.multiply(oldCost).add(addedQty.multiply(addedCost));
        return unitCost(divide(totalValue, totalQty));
    }
}
