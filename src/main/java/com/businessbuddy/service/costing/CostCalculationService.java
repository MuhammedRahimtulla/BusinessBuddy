package com.businessbuddy.service.costing;

import com.businessbuddy.entity.Ingredient;
import com.businessbuddy.entity.RecipeIngredient;
import com.businessbuddy.entity.RecipeVersion;
import com.businessbuddy.exception.BusinessRuleException;
import com.businessbuddy.util.Money;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The automated costing engine (spec 8.11, pseudocode 4.6.5).
 *
 * <pre>
 * line cost        = base quantity x (1 + waste %) x average cost per base unit
 * ingredient cost  = sum of line costs
 * overhead         = fixed overhead per batch + ingredient cost x overhead %   (decision D6)
 * batch cost       = ingredient cost + labour + packaging + utilities + overhead
 * unit cost        = batch cost / batch output quantity
 * </pre>
 *
 * Waste is applied once, on each line only (decision D7). The result is the current
 * estimate; historical costs come from the snapshots stored on batches and sales.
 * All costing logic lives here so it is never duplicated in controllers or JavaScript.
 */
@Service
public class CostCalculationService {

    private final UnitConversionService units;

    public CostCalculationService(UnitConversionService units) {
        this.units = units;
    }

    public CostBreakdown.LineCost lineCost(RecipeIngredient line) {
        Ingredient ingredient = line.getIngredient();
        BigDecimal baseQty = units.convert(line.getQuantity(), line.getUnit(), ingredient.getBaseUnit());
        BigDecimal waste = Money.nz(line.getWastePercent());
        BigDecimal withWaste = baseQty.multiply(BigDecimal.ONE.add(waste));
        BigDecimal cost = withWaste.multiply(ingredient.getAverageUnitCost());
        return new CostBreakdown.LineCost(ingredient.getId(), ingredient.getName(), Money.quantity(baseQty),
                waste, Money.quantity(withWaste), ingredient.getAverageUnitCost(), Money.unitCost(cost));
    }

    /** Current estimated cost of one batch of a recipe version and of one unit of output. */
    public CostBreakdown batchCost(RecipeVersion recipe) {
        if (recipe.getBatchOutputQty() == null || recipe.getBatchOutputQty().signum() <= 0) {
            throw new BusinessRuleException("Batch output must be greater than zero");
        }
        List<CostBreakdown.LineCost> lines = new ArrayList<>();
        BigDecimal ingredientCost = BigDecimal.ZERO;
        for (RecipeIngredient line : recipe.getIngredients()) {
            CostBreakdown.LineCost lc = lineCost(line);
            lines.add(lc);
            ingredientCost = ingredientCost.add(lc.lineCost());
        }
        return totals(recipe, lines, ingredientCost, BigDecimal.ONE, recipe.getBatchOutputQty());
    }

    /**
     * Adds the per-batch costs of a recipe to an ingredient cost and divides by the output.
     * Used for the recipe estimate (scale 1) and for a confirmed production batch, where
     * the ingredient cost comes from the quantities actually consumed and the per-batch
     * costs are scaled by planned output / recipe output (4.6.7).
     */
    public CostBreakdown totals(RecipeVersion recipe, List<CostBreakdown.LineCost> lines, BigDecimal ingredientCost,
                                BigDecimal scale, BigDecimal outputQuantity) {
        if (outputQuantity == null || outputQuantity.signum() <= 0) {
            throw new BusinessRuleException("Output quantity must be greater than zero");
        }
        BigDecimal labour = Money.nz(recipe.getLabourCost()).multiply(scale);
        BigDecimal packaging = Money.nz(recipe.getPackagingCost()).multiply(scale);
        BigDecimal utilities = Money.nz(recipe.getUtilitiesCost()).multiply(scale);
        BigDecimal overhead = Money.nz(recipe.getOverheadFixed()).multiply(scale)
                .add(ingredientCost.multiply(Money.nz(recipe.getOverheadPercent())));
        BigDecimal batch = ingredientCost.add(labour).add(packaging).add(utilities).add(overhead);
        BigDecimal unit = Money.unitCost(Money.divide(batch, outputQuantity));
        return new CostBreakdown(List.copyOf(lines), Money.unitCost(ingredientCost), Money.unitCost(labour),
                Money.unitCost(packaging), Money.unitCost(utilities), Money.unitCost(overhead),
                Money.unitCost(batch), outputQuantity, unit);
    }
}
