package com.businessbuddy.service;

import com.businessbuddy.audit.AuditService;
import com.businessbuddy.dto.CatalogForms.RecipeForm;
import com.businessbuddy.dto.CatalogForms.RecipeLineForm;
import com.businessbuddy.entity.Ingredient;
import com.businessbuddy.entity.Product;
import com.businessbuddy.entity.RecipeIngredient;
import com.businessbuddy.entity.RecipeVersion;
import com.businessbuddy.entity.UnitOfMeasure;
import com.businessbuddy.exception.BusinessRuleException;
import com.businessbuddy.exception.NotFoundException;
import com.businessbuddy.repository.RecipeVersionRepository;
import com.businessbuddy.security.BusinessContext;
import com.businessbuddy.service.costing.CostBreakdown;
import com.businessbuddy.service.costing.CostCalculationService;
import com.businessbuddy.service.costing.UnitConversionService;
import com.businessbuddy.util.Money;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Versioned recipes and their cost (spec 8.9, 8.11).
 *
 * <p>A saved version is never edited. A change creates the next version, so production
 * batches keep pointing at the exact version they followed. Only one version is active.</p>
 */
@Service
public class RecipeService {

    private final RecipeVersionRepository recipes;
    private final CatalogService catalog;
    private final CostCalculationService costing;
    private final UnitConversionService units;
    private final BusinessContext context;
    private final AuditService audit;

    public RecipeService(RecipeVersionRepository recipes, CatalogService catalog, CostCalculationService costing,
                         UnitConversionService units, BusinessContext context, AuditService audit) {
        this.recipes = recipes;
        this.catalog = catalog;
        this.costing = costing;
        this.units = units;
        this.context = context;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<RecipeVersion> versions(Long productId) {
        catalog.product(productId);                          // ownership check
        return recipes.findByProductIdOrderByVersionNoDesc(productId);
    }

    @Transactional(readOnly = true)
    public Optional<RecipeVersion> activeRecipe(Long productId) {
        catalog.product(productId);
        return recipes.findByProductIdAndActiveTrue(productId);
    }

    @Transactional(readOnly = true)
    public RecipeVersion version(Long versionId) {
        RecipeVersion v = recipes.findOwned(versionId, context.currentBusinessId())
                .orElseThrow(() -> new NotFoundException("Recipe version"));
        v.getIngredients().forEach(line -> line.getIngredient().getName());   // load lines for the view
        return v;
    }

    /** Pre-fills the form for a new version from an existing one. */
    @Transactional(readOnly = true)
    public RecipeForm formFrom(RecipeVersion v) {
        RecipeForm form = new RecipeForm();
        form.setName(v.getName());
        form.setBatchOutputQty(v.getBatchOutputQty());
        form.setOutputUnitCode(v.getOutputUnit().getCode());
        form.setLabourCost(v.getLabourCost());
        form.setPackagingCost(v.getPackagingCost());
        form.setUtilitiesCost(v.getUtilitiesCost());
        form.setOverheadFixed(v.getOverheadFixed());
        form.setOverheadPercent(Money.rateToPercent(v.getOverheadPercent()));
        form.setPreparationNotes(v.getPreparationNotes());
        for (RecipeIngredient line : v.getIngredients()) {
            RecipeLineForm lf = new RecipeLineForm();
            lf.setIngredientId(line.getIngredient().getId());
            lf.setQuantity(line.getQuantity());
            lf.setUnitCode(line.getUnit().getCode());
            lf.setWastePercent(Money.rateToPercent(line.getWastePercent()));
            form.getLines().add(lf);
        }
        return form;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public RecipeVersion createVersion(Long productId, RecipeForm form) {
        Product product = catalog.product(productId);
        if (!product.isManufactured()) {
            throw new BusinessRuleException("Only manufactured products have recipes");
        }
        RecipeVersion v = build(product, form);
        v.setVersionNo(recipes.maxVersionNo(productId) + 1);
        v.setCreatedBy(context.currentUserEntity());
        if (form.isMakeActive()) {
            recipes.deactivateAll(productId);
            v.setActive(true);
        }
        recipes.save(v);
        audit.record("RECIPE_VERSION_CREATED", "RECIPE_VERSION", v.getId(),
                Map.of("product", product.getName(), "version", v.getVersionNo(), "active", v.isActive()));
        return v;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public void activate(Long versionId) {
        RecipeVersion v = version(versionId);
        if (v.isActive()) {
            return;                       // already active; the bulk update below would clear it
        }
        recipes.deactivateAll(v.getProduct().getId());
        v.setActive(true);
        audit.record("RECIPE_VERSION_ACTIVATED", "RECIPE_VERSION", v.getId());
    }

    @Transactional(readOnly = true)
    public CostBreakdown cost(Long versionId) {
        return costing.batchCost(version(versionId));
    }

    /** Cost of an unsaved form, for the live breakdown beside the recipe form (4.7.2). */
    @Transactional(readOnly = true)
    public CostBreakdown previewCost(Long productId, RecipeForm form) {
        return costing.batchCost(build(catalog.product(productId), form));
    }

    /** Current estimated unit cost of a product: its active recipe, or average cost for resale items. */
    @Transactional(readOnly = true)
    public Optional<CostBreakdown> currentCost(Product product) {
        if (!product.isManufactured()) {
            return Optional.empty();
        }
        return recipes.findByProductIdAndActiveTrue(product.getId()).map(costing::batchCost);
    }

    /** Validates a recipe form and builds an unsaved version from it. */
    private RecipeVersion build(Product product, RecipeForm form) {
        List<RecipeLineForm> lines = form.getLines().stream().filter(l -> !l.isBlank()).toList();
        if (lines.isEmpty()) {
            throw new BusinessRuleException("Add at least one ingredient");
        }
        RecipeVersion v = new RecipeVersion();
        v.setProduct(product);
        v.setName(form.getName().trim());
        v.setBatchOutputQty(Money.quantity(form.getBatchOutputQty()));
        v.setOutputUnit(catalog.unit(form.getOutputUnitCode()));
        v.setLabourCost(Money.money(form.getLabourCost()));
        v.setPackagingCost(Money.money(form.getPackagingCost()));
        v.setUtilitiesCost(Money.money(form.getUtilitiesCost()));
        v.setOverheadFixed(Money.money(form.getOverheadFixed()));
        v.setOverheadPercent(Money.percentToRate(form.getOverheadPercent()));
        v.setEffectiveFrom(form.getEffectiveFrom());
        v.setPreparationNotes(BusinessService.blankToNull(form.getPreparationNotes()));
        Set<Long> seen = new HashSet<>();
        for (RecipeLineForm lf : lines) {
            if (!seen.add(lf.getIngredientId())) {
                throw new BusinessRuleException("Each ingredient may appear only once in a recipe");
            }
            if (lf.getQuantity() == null || lf.getQuantity().signum() <= 0) {
                throw new BusinessRuleException("Every ingredient line needs a quantity above zero");
            }
            Ingredient ingredient = catalog.ingredient(lf.getIngredientId());
            UnitOfMeasure unit = lf.getUnitCode() == null || lf.getUnitCode().isBlank()
                    ? ingredient.getBaseUnit() : catalog.unit(lf.getUnitCode());
            if (!units.compatible(unit, ingredient.getBaseUnit())) {
                throw new BusinessRuleException("The unit for " + ingredient.getName() + " must measure "
                        + ingredient.getBaseUnit().getDimension().name().toLowerCase());
            }
            RecipeIngredient line = new RecipeIngredient();
            line.setIngredient(ingredient);
            line.setQuantity(Money.quantity(lf.getQuantity()));
            line.setUnit(unit);
            line.setWastePercent(Money.percentToRate(lf.getWastePercent()));
            v.addIngredient(line);
        }
        return v;
    }
}
