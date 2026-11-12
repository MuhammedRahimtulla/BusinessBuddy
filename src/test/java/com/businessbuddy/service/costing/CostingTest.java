package com.businessbuddy.service.costing;

import com.businessbuddy.entity.Enums.Dimension;
import com.businessbuddy.entity.Ingredient;
import com.businessbuddy.entity.RecipeIngredient;
import com.businessbuddy.entity.RecipeVersion;
import com.businessbuddy.entity.UnitOfMeasure;
import com.businessbuddy.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests of UnitConversionService and CostCalculationService (FR-07, FR-09, FR-11).
 * Uses the Chocolate Cake example of the specification (section 8.9) with known costs,
 * so every expected value can be checked by hand.
 */
class CostingTest {

    static final UnitOfMeasure G = new UnitOfMeasure("g", "Gram", Dimension.MASS, BigDecimal.ONE);
    static final UnitOfMeasure KG = new UnitOfMeasure("kg", "Kilogram", Dimension.MASS, new BigDecimal("1000"));
    static final UnitOfMeasure ML = new UnitOfMeasure("ml", "Millilitre", Dimension.VOLUME, BigDecimal.ONE);
    static final UnitOfMeasure L = new UnitOfMeasure("l", "Litre", Dimension.VOLUME, new BigDecimal("1000"));
    static final UnitOfMeasure UNIT = new UnitOfMeasure("unit", "Unit", Dimension.COUNT, BigDecimal.ONE);
    static final UnitOfMeasure DOZEN = new UnitOfMeasure("dozen", "Dozen", Dimension.COUNT, new BigDecimal("12"));

    private final UnitConversionService units = new UnitConversionService();
    private final CostCalculationService costing = new CostCalculationService(units);
    private RecipeVersion cake;

    static Ingredient ingredient(String name, UnitOfMeasure base, String avgCost) {
        Ingredient i = new Ingredient();
        i.setName(name);
        i.setBaseUnit(base);
        i.setAverageUnitCost(new BigDecimal(avgCost));
        return i;
    }

    static RecipeIngredient line(Ingredient i, String qty, UnitOfMeasure unit, String waste) {
        RecipeIngredient l = new RecipeIngredient();
        l.setIngredient(i);
        l.setQuantity(new BigDecimal(qty));
        l.setUnit(unit);
        l.setWastePercent(new BigDecimal(waste));
        return l;
    }

    @BeforeEach
    void chocolateCake() {
        cake = new RecipeVersion();
        cake.setBatchOutputQty(new BigDecimal("10"));
        cake.addIngredient(line(ingredient("Flour", G, "0.0200"), "500", G, "0"));       // 10.00
        cake.addIngredient(line(ingredient("Sugar", G, "0.0180"), "300", G, "0"));       //  5.40
        cake.addIngredient(line(ingredient("Eggs", UNIT, "2.5000"), "4", UNIT, "0"));    // 10.00
        cake.addIngredient(line(ingredient("Butter", G, "0.1200"), "250", G, "0"));      // 30.00
        cake.addIngredient(line(ingredient("Chocolate", G, "0.1500"), "200", G, "0"));   // 30.00
        cake.setLabourCost(new BigDecimal("20.00"));
        cake.setPackagingCost(new BigDecimal("5.00"));
        cake.setUtilitiesCost(new BigDecimal("3.00"));
        cake.setOverheadFixed(new BigDecimal("2.00"));
        cake.setOverheadPercent(new BigDecimal("0.1000"));
    }

    @Test
    @DisplayName("TC-UC-01: 2 kg converts to 2000 g")
    void kilogramsToGrams() {
        assertThat(units.convert(new BigDecimal("2"), KG, G)).isEqualByComparingTo("2000");
    }

    @Test
    @DisplayName("TC-UC-02: 2 dozen converts to 24 units and 1.5 l to 1500 ml")
    void countAndVolume() {
        assertThat(units.convert(new BigDecimal("2"), DOZEN, UNIT)).isEqualByComparingTo("24");
        assertThat(units.convert(new BigDecimal("1.5"), L, ML)).isEqualByComparingTo("1500");
        assertThat(units.convert(new BigDecimal("250"), G, KG)).isEqualByComparingTo("0.25");
    }

    @Test
    @DisplayName("TC-UC-03: incompatible units (grams to litres) are rejected")
    void incompatibleUnits() {
        assertThatThrownBy(() -> units.convert(BigDecimal.ONE, G, L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Cannot convert");
    }

    @Test
    @DisplayName("TC-COST-01: ingredient line cost = base quantity x average cost")
    void lineCost() {
        assertThat(costing.lineCost(cake.getIngredients().get(0)).lineCost()).isEqualByComparingTo("10.00");
        RecipeIngredient inKg = line(ingredient("Flour", G, "0.0200"), "0.5", KG, "0");
        assertThat(costing.lineCost(inKg).lineCost()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("TC-COST-02: chocolate cake batch cost 123.94 and unit cost 12.394")
    void batchAndUnitCost() {
        CostBreakdown b = costing.batchCost(cake);
        assertThat(b.ingredientCost()).isEqualByComparingTo("85.40");
        assertThat(b.overheadCost()).isEqualByComparingTo("10.54");       // 2.00 + 10% of 85.40
        assertThat(b.batchCost()).isEqualByComparingTo("123.94");         // 85.40 + 20 + 5 + 3 + 10.54
        assertThat(b.unitCost()).isEqualByComparingTo("12.3940");
    }

    @Test
    @DisplayName("TC-COST-03: waste allowance is applied once, on the line only")
    void wasteAppliedOnce() {
        cake.getIngredients().get(0).setWastePercent(new BigDecimal("0.10"));   // flour +10% => 11.00
        CostBreakdown b = costing.batchCost(cake);
        assertThat(b.lines().get(0).lineCost()).isEqualByComparingTo("11.00");
        assertThat(b.ingredientCost()).isEqualByComparingTo("86.40");
        // overhead percent is based on ingredient cost incl. waste, not on a second waste charge
        assertThat(b.overheadCost()).isEqualByComparingTo("10.64");
    }

    @Test
    @DisplayName("TC-COST-04: zero batch output is rejected")
    void zeroOutput() {
        cake.setBatchOutputQty(BigDecimal.ZERO);
        assertThatThrownBy(() -> costing.batchCost(cake)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("TC-COST-05: per-batch costs scale with batch size for production (2 batches)")
    void scaledTotals() {
        CostBreakdown b = costing.totals(cake, java.util.List.of(), new BigDecimal("170.80"),
                new BigDecimal("2"), new BigDecimal("20"));
        // 170.80 + 2x(20+5+3+2) + 10% x 170.80 = 170.80 + 60 + 17.08 = 247.88
        assertThat(b.batchCost()).isEqualByComparingTo("247.88");
        assertThat(b.unitCost()).isEqualByComparingTo("12.3940");
    }

    @Test
    @DisplayName("TC-COST-06: lower actual output raises the unit cost")
    void lossRaisesUnitCost() {
        CostBreakdown b = costing.totals(cake, java.util.List.of(), new BigDecimal("85.40"), BigDecimal.ONE,
                new BigDecimal("8"));
        assertThat(b.unitCost()).isEqualByComparingTo("15.4925");        // 123.94 / 8
    }
}
