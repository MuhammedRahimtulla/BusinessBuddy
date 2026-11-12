package com.businessbuddy.service.pricing;

import com.businessbuddy.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests of the pricing engine (FR-12, spec 8.13 and 16 "Pricing tests"). */
class PricingServiceTest {

    private final PricingService pricing = new PricingService();
    private static final BigDecimal COST = new BigDecimal("12.394");

    @Test
    @DisplayName("TC-PRICE-01: 40% target margin gives price = cost / 0.6 = 20.66")
    void priceForMargin() {
        assertThat(pricing.priceForMargin(COST, new BigDecimal("0.40"))).isEqualByComparingTo("20.66");
    }

    @Test
    @DisplayName("TC-PRICE-02: 50% markup gives price = cost x 1.5 = 18.59")
    void priceForMarkup() {
        assertThat(pricing.priceForMarkup(COST, new BigDecimal("0.50"))).isEqualByComparingTo("18.59");
    }

    @Test
    @DisplayName("TC-PRICE-03: margin and markup are different measures of the same price")
    void marginVersusMarkup() {
        PriceAnalysis a = pricing.analyse(new BigDecimal("10.00"), new BigDecimal("15.00"), BigDecimal.ZERO);
        assertThat(a.margin()).isEqualByComparingTo("0.3333");           // 5 / 15
        assertThat(a.markup()).isEqualByComparingTo("0.5000");           // 5 / 10
        assertThat(a.profitPerUnit()).isEqualByComparingTo("5.00");
    }

    @Test
    @DisplayName("TC-PRICE-04: 15% tax is added on top of the price excluding tax")
    void taxAdded() {
        PriceAnalysis a = pricing.analyse(new BigDecimal("10.00"), new BigDecimal("20.00"), new BigDecimal("0.15"));
        assertThat(a.taxAmount()).isEqualByComparingTo("3.00");
        assertThat(a.priceIncludingTax()).isEqualByComparingTo("23.00");
        assertThat(a.priceExcludingTax()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("TC-PRICE-05: a price below unit cost is flagged")
    void belowCost() {
        PriceAnalysis a = pricing.analyse(COST, new BigDecimal("11.00"), BigDecimal.ZERO);
        assertThat(a.belowCost()).isTrue();
        assertThat(a.profitPerUnit()).isNegative();
        assertThat(pricing.analyse(COST, new BigDecimal("13.00"), BigDecimal.ZERO).belowCost()).isFalse();
    }

    @Test
    @DisplayName("TC-PRICE-06: a margin of 100% or more, or below 0%, is rejected")
    void invalidMargin() {
        assertThatThrownBy(() -> pricing.priceForMargin(COST, BigDecimal.ONE)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> pricing.priceForMargin(COST, new BigDecimal("-0.1"))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> pricing.priceForMarkup(COST, new BigDecimal("-0.1"))).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("TC-PRICE-07: margin is undefined for a zero price; markup undefined for zero cost")
    void undefinedRatios() {
        assertThat(pricing.analyse(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO).margin()).isNull();
        assertThat(pricing.analyse(BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ZERO).markup()).isNull();
    }

    @Test
    @DisplayName("TC-PRICE-08: prices are rounded HALF_UP to cents; break-even equals unit cost")
    void roundingAndBreakEven() {
        // 10 / (1 - 0.33) = 14.925373... -> 14.93
        assertThat(pricing.priceForMargin(new BigDecimal("10"), new BigDecimal("0.33"))).isEqualByComparingTo("14.93");
        PriceAnalysis a = pricing.suggest(COST, PricingService.Method.PRICE, new BigDecimal("20"), BigDecimal.ZERO);
        assertThat(a.breakEvenPrice()).isEqualByComparingTo("12.39");
    }
}
