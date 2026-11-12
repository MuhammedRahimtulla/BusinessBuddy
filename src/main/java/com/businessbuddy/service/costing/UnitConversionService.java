package com.businessbuddy.service.costing;

import com.businessbuddy.entity.UnitOfMeasure;
import com.businessbuddy.exception.BusinessRuleException;
import com.businessbuddy.util.Money;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Converts quantities between units of the same dimension (8.4, pseudocode ToBase in 4.6.3).
 * Example: 2 kg to g = 2 x 1000 / 1 = 2000 g. Units of different dimensions (for example
 * grams and litres) are never combined without an explicit rule, so they are rejected.
 */
@Service
public class UnitConversionService {

    public BigDecimal convert(BigDecimal quantity, UnitOfMeasure from, UnitOfMeasure to) {
        if (quantity == null) {
            throw new BusinessRuleException("Enter a quantity");
        }
        if (from.equals(to)) {
            return quantity;
        }
        if (from.getDimension() != to.getDimension()) {
            throw new BusinessRuleException("Cannot convert " + from.getName() + " to " + to.getName());
        }
        return Money.divide(quantity.multiply(from.getFactorToBase()), to.getFactorToBase());
    }

    /** Converts to the stock unit and rounds to the 3 decimals stored for quantities. */
    public BigDecimal toStockQuantity(BigDecimal quantity, UnitOfMeasure from, UnitOfMeasure stockUnit) {
        return Money.quantity(convert(quantity, from, stockUnit));
    }

    public boolean compatible(UnitOfMeasure a, UnitOfMeasure b) {
        return a.getDimension() == b.getDimension();
    }
}
