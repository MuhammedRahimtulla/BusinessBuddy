package com.businessbuddy.service.pricing;

import com.businessbuddy.exception.BusinessRuleException;
import com.businessbuddy.util.Money;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * The pricing engine (spec 8.13, pseudocode 4.6.6).
 *
 * <pre>
 * price for a target margin m  = unit cost / (1 - m)      margin = profit / price
 * price for a markup k         = unit cost x (1 + k)      markup = profit / cost
 * </pre>
 *
 * Margin and markup are kept strictly separate. Prices are excluding tax (decision D5);
 * tax is added for display using the configured rate. Results are rounded to cents.
 */
@Service
public class PricingService {

    public enum Method { MARGIN, MARKUP, PRICE }

    public BigDecimal priceForMargin(BigDecimal unitCost, BigDecimal margin) {
        requireCost(unitCost);
        if (margin == null || margin.signum() < 0 || margin.compareTo(BigDecimal.ONE) >= 0) {
            throw new BusinessRuleException("Margin must be at least 0% and below 100%");
        }
        return Money.money(Money.divide(unitCost, BigDecimal.ONE.subtract(margin)));
    }

    public BigDecimal priceForMarkup(BigDecimal unitCost, BigDecimal markup) {
        requireCost(unitCost);
        if (markup == null || markup.signum() < 0) {
            throw new BusinessRuleException("Markup cannot be negative");
        }
        return Money.money(unitCost.multiply(BigDecimal.ONE.add(markup)));
    }

    /** Suggests a price by margin or markup, or analyses a proposed price, in one call. */
    public PriceAnalysis suggest(BigDecimal unitCost, Method method, BigDecimal value, BigDecimal taxRate) {
        BigDecimal price = switch (method) {
            case MARGIN -> priceForMargin(unitCost, value);
            case MARKUP -> priceForMarkup(unitCost, value);
            case PRICE -> {
                if (value == null || value.signum() < 0) {
                    throw new BusinessRuleException("Price cannot be negative");
                }
                yield Money.money(value);
            }
        };
        return analyse(unitCost, price, taxRate);
    }

    public PriceAnalysis analyse(BigDecimal unitCost, BigDecimal price, BigDecimal taxRate) {
        requireCost(unitCost);
        BigDecimal rate = Money.nz(taxRate);
        if (rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) >= 0) {
            throw new BusinessRuleException("Tax rate must be at least 0% and below 100%");
        }
        BigDecimal profit = price.subtract(unitCost);
        BigDecimal margin = price.signum() > 0 ? Money.divide(profit, price).setScale(4, Money.ROUNDING) : null;
        BigDecimal markup = unitCost.signum() > 0 ? Money.divide(profit, unitCost).setScale(4, Money.ROUNDING) : null;
        BigDecimal tax = Money.money(price.multiply(rate));
        return new PriceAnalysis(unitCost, price, Money.unitCost(profit), margin, markup, rate, tax,
                price.add(tax), Money.money(unitCost), price.compareTo(unitCost) < 0);
    }

    private static void requireCost(BigDecimal unitCost) {
        if (unitCost == null || unitCost.signum() < 0) {
            throw new BusinessRuleException("Unit cost cannot be negative");
        }
    }
}
