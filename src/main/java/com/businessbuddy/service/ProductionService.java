package com.businessbuddy.service;

import com.businessbuddy.audit.AuditService;
import com.businessbuddy.dto.TransactionForms.ProductionForm;
import com.businessbuddy.entity.Business;
import com.businessbuddy.entity.Enums.BatchStatus;
import com.businessbuddy.entity.Enums.MovementType;
import com.businessbuddy.entity.Enums.SourceType;
import com.businessbuddy.entity.Ingredient;
import com.businessbuddy.entity.Product;
import com.businessbuddy.entity.ProductionBatch;
import com.businessbuddy.entity.ProductionConsumption;
import com.businessbuddy.entity.RecipeIngredient;
import com.businessbuddy.entity.RecipeVersion;
import com.businessbuddy.exception.BusinessRuleException;
import com.businessbuddy.exception.NotFoundException;
import com.businessbuddy.repository.ProductionBatchRepository;
import com.businessbuddy.repository.ProductionConsumptionRepository;
import com.businessbuddy.repository.RecipeVersionRepository;
import com.businessbuddy.security.BusinessContext;
import com.businessbuddy.service.costing.CostBreakdown;
import com.businessbuddy.service.costing.CostCalculationService;
import com.businessbuddy.service.costing.UnitConversionService;
import com.businessbuddy.service.inventory.InventoryService;
import com.businessbuddy.util.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Production batches (spec 8.10, pseudocode 4.6.7).
 *
 * <p>Confirming a batch consumes ingredients and adds finished stock in one transaction.
 * If the user enters the actual quantity used, it replaces the recipe quantity plus waste,
 * so waste is still counted once. The unit cost is divided by the actual output, so losses
 * raise the cost per unit.</p>
 */
@Service
public class ProductionService {

    private final ProductionBatchRepository batches;
    private final ProductionConsumptionRepository consumptions;
    private final RecipeVersionRepository recipes;
    private final CatalogService catalog;
    private final InventoryService inventory;
    private final CostCalculationService costing;
    private final UnitConversionService units;
    private final BusinessContext context;
    private final AuditService audit;

    public ProductionService(ProductionBatchRepository batches, ProductionConsumptionRepository consumptions,
                             RecipeVersionRepository recipes, CatalogService catalog, InventoryService inventory,
                             CostCalculationService costing, UnitConversionService units, BusinessContext context,
                             AuditService audit) {
        this.batches = batches;
        this.consumptions = consumptions;
        this.recipes = recipes;
        this.catalog = catalog;
        this.inventory = inventory;
        this.costing = costing;
        this.units = units;
        this.context = context;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public ProductionBatch batch(Long id) {
        ProductionBatch b = batches.findByIdAndBusinessId(id, context.currentBusinessId())
                .orElseThrow(() -> new NotFoundException("Production batch"));
        b.getProduct().getName();
        b.getRecipeVersion().getIngredients().forEach(l -> l.getIngredient().getName());
        b.getConsumptions().forEach(c -> c.getIngredient().getName());
        return b;
    }

    @Transactional
    public ProductionBatch createDraft(ProductionForm form) {
        Product product = catalog.product(form.getProductId());
        if (!product.isManufactured() || !product.isActive()) {
            throw new BusinessRuleException("Choose an active manufactured product");
        }
        RecipeVersion recipe = recipes.findByProductIdAndActiveTrue(product.getId())
                .orElseThrow(() -> new BusinessRuleException(product.getName() + " has no active recipe"));
        Business business = context.currentBusiness();
        if (form.getProductionDate().isAfter(LocalDate.now(business.zoneId()))) {
            throw new BusinessRuleException("The production date cannot be in the future");
        }
        ProductionBatch batch = new ProductionBatch();
        batch.setBusiness(business);
        batch.setProduct(product);
        batch.setRecipeVersion(recipe);
        batch.setPlannedOutput(Money.quantity(form.getPlannedOutput()));
        batch.setProductionDate(form.getProductionDate());
        batch.setWasteNotes(BusinessService.blankToNull(form.getWasteNotes()));
        batch.setProducedBy(context.currentUserEntity());
        batches.save(batch);
        audit.record("BATCH_CREATED", "PRODUCTION_BATCH", batch.getId());
        return batch;
    }

    /** Quantity of one ingredient the recipe expects for this batch, in its base unit, incl. waste. */
    public record PlannedLine(Ingredient ingredient, BigDecimal plannedQuantity, BigDecimal inStock) {
        public boolean shortage() {
            return inStock.compareTo(plannedQuantity) < 0;
        }
    }

    @Transactional(readOnly = true)
    public List<PlannedLine> plannedConsumption(ProductionBatch batch) {
        RecipeVersion recipe = batch.getRecipeVersion();
        BigDecimal scale = Money.divide(batch.getPlannedOutput(), recipe.getBatchOutputQty());
        List<PlannedLine> lines = new ArrayList<>();
        for (RecipeIngredient line : recipe.getIngredients()) {
            lines.add(new PlannedLine(line.getIngredient(), planned(line, scale), line.getIngredient().getCurrentQuantity()));
        }
        return lines;
    }

    private BigDecimal planned(RecipeIngredient line, BigDecimal scale) {
        BigDecimal base = units.convert(line.getQuantity(), line.getUnit(), line.getIngredient().getBaseUnit());
        return Money.quantity(base.multiply(BigDecimal.ONE.add(line.getWastePercent())).multiply(scale));
    }

    /**
     * Confirms a draft batch. {@code actualUsed} maps ingredient id to the quantity actually
     * used (base unit); ingredients not in the map use the recipe quantity plus waste.
     */
    @Transactional
    public ProductionBatch confirm(Long id, BigDecimal actualOutput, Map<Long, BigDecimal> actualUsed, String wasteNotes) {
        Long businessId = context.currentBusinessId();
        ProductionBatch batch = batches.lockById(id).filter(b -> b.owningBusinessId().equals(businessId))
                .orElseThrow(() -> new NotFoundException("Production batch"));
        if (batch.getStatus() == BatchStatus.CONFIRMED) {
            return batch;                                   // resubmission changes nothing
        }
        if (batch.getStatus() != BatchStatus.DRAFT || actualOutput == null || actualOutput.signum() <= 0) {
            throw new BusinessRuleException("Enter an actual output greater than zero for a draft batch");
        }
        RecipeVersion recipe = batch.getRecipeVersion();
        BigDecimal scale = Money.divide(batch.getPlannedOutput(), recipe.getBatchOutputQty());
        Instant when = batch.getProductionDate().atStartOfDay(batch.getBusiness().zoneId()).toInstant().plusSeconds(6 * 3600);

        BigDecimal ingredientCost = BigDecimal.ZERO;
        List<CostBreakdown.LineCost> lines = new ArrayList<>();
        for (RecipeIngredient line : recipe.getIngredients()) {
            Ingredient ingredient = inventory.lockIngredient(line.getIngredient().getId());
            BigDecimal used = actualUsed == null ? null : actualUsed.get(ingredient.getId());
            if (used == null) {
                used = planned(line, scale);
            } else if (used.signum() <= 0) {
                throw new BusinessRuleException("Actual quantity used must be above zero for " + ingredient.getName());
            }
            used = Money.quantity(used);
            BigDecimal unitCost = ingredient.getAverageUnitCost();
            BigDecimal lineCost = Money.unitCost(used.multiply(unitCost));

            ProductionConsumption consumption = new ProductionConsumption();
            consumption.setIngredient(ingredient);
            consumption.setQuantityBase(used);
            consumption.setUnitCostSnapshot(unitCost);
            consumption.setLineCost(lineCost);
            batch.addConsumption(consumption);
            consumptions.save(consumption);

            inventory.apply(ingredient, new InventoryService.Change(MovementType.PRODUCTION_CONSUMPTION, used.negate(),
                    unitCost, SourceType.PRODUCTION_CONSUMPTION, consumption.getId(), null, null, when,
                    context.currentUserEntity()));
            ingredientCost = ingredientCost.add(lineCost);
            lines.add(new CostBreakdown.LineCost(ingredient.getId(), ingredient.getName(), used, BigDecimal.ZERO,
                    used, unitCost, lineCost));
        }

        CostBreakdown cost = costing.totals(recipe, lines, ingredientCost, scale, Money.quantity(actualOutput));
        batch.setActualOutput(Money.quantity(actualOutput));
        batch.setTotalCost(Money.money(cost.batchCost()));
        batch.setUnitCost(cost.unitCost());
        if (wasteNotes != null && !wasteNotes.isBlank()) {
            batch.setWasteNotes(wasteNotes.trim());
        }

        Product product = inventory.lockProduct(batch.getProduct().getId());
        inventory.apply(product, new InventoryService.Change(MovementType.PRODUCTION_OUTPUT, batch.getActualOutput(),
                cost.unitCost(), SourceType.PRODUCTION_BATCH, batch.getId(), null, null, when,
                context.currentUserEntity()));
        batch.setStatus(BatchStatus.CONFIRMED);
        audit.record("BATCH_CONFIRMED", "PRODUCTION_BATCH", batch.getId(), Map.of(
                "output", batch.getActualOutput().toPlainString(), "unitCost", batch.getUnitCost().toPlainString()));
        return batch;
    }

    /** Cancels a draft batch. A confirmed batch is corrected with stock adjustments instead. */
    @Transactional
    public void cancel(Long id) {
        ProductionBatch batch = batch(id);
        if (batch.getStatus() != BatchStatus.DRAFT) {
            throw new BusinessRuleException("Only a draft batch can be cancelled");
        }
        batch.setStatus(BatchStatus.CANCELLED);
        audit.record("BATCH_CANCELLED", "PRODUCTION_BATCH", batch.getId());
    }
}
