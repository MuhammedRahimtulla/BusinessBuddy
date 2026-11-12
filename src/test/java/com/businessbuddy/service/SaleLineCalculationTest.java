package com.businessbuddy.service;

import com.businessbuddy.entity.SaleItem;
import com.businessbuddy.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests of sale line totals: discount, tax and rounding (FR-13). */
class SaleLineCalculationTest {

    private static SaleItem item(String qty, String price, String discount, String taxRate) {
        SaleItem i = new SaleItem();
        i.setQuantity(new BigDecimal(qty));
        i.setUnitPrice(new BigDecimal(price));
        i.setDiscountAmount(new BigDecimal(discount));
        i.setTaxRate(new BigDecimal(taxRate));
        return i;
    }

    @Test
    @DisplayName("TC-SALE-01: 3 x 38.00 less 4.00 discount with 15% tax = 110.00 + 16.50 = 126.50")
    void lineTotals() {
        SaleItem i = item("3", "38.00", "4.00", "0.15");
        SalesService.calculateLine(i);
        assertThat(i.getNetAmount()).isEqualByComparingTo("110.00");
        assertThat(i.getTaxAmount()).isEqualByComparingTo("16.50");
        assertThat(i.getLineTotal()).isEqualByComparingTo("126.50");
    }

    @Test
    @DisplayName("TC-SALE-02: tax is rounded to cents HALF_UP (15% of 22.03 = 3.3045 -> 3.30)")
    void taxRounding() {
        SaleItem i = item("1", "22.03", "0", "0.15");
        SalesService.calculateLine(i);
        assertThat(i.getTaxAmount()).isEqualByComparingTo("3.30");   // 3.3045 -> 3.30
        assertThat(i.getLineTotal()).isEqualByComparingTo("25.33");
    }

    @Test
    @DisplayName("TC-SALE-03: a discount larger than the line value is rejected")
    void discountTooLarge() {
        SaleItem i = item("1", "10.00", "10.01", "0");
        assertThatThrownBy(() -> SalesService.calculateLine(i)).isInstanceOf(BusinessRuleException.class);
    }
}
