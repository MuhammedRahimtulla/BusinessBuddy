package com.businessbuddy.service;

import com.businessbuddy.audit.AuditService;
import com.businessbuddy.dto.TransactionForms.PurchaseForm;
import com.businessbuddy.dto.TransactionForms.PurchaseLineForm;
import com.businessbuddy.entity.Business;
import com.businessbuddy.entity.Enums.MovementType;
import com.businessbuddy.entity.Enums.NotificationNature;
import com.businessbuddy.entity.Enums.NotificationType;
import com.businessbuddy.entity.Enums.ProductType;
import com.businessbuddy.entity.Enums.PurchaseStatus;
import com.businessbuddy.entity.Enums.SourceType;
import com.businessbuddy.entity.Ingredient;
import com.businessbuddy.entity.InventoryMovement;
import com.businessbuddy.entity.Product;
import com.businessbuddy.entity.Purchase;
import com.businessbuddy.entity.PurchaseItem;
import com.businessbuddy.entity.StockItem;
import com.businessbuddy.entity.Supplier;
import com.businessbuddy.entity.UnitOfMeasure;
import com.businessbuddy.exception.BusinessRuleException;
import com.businessbuddy.exception.NotFoundException;
import com.businessbuddy.repository.InventoryMovementRepository;
import com.businessbuddy.repository.PurchaseRepository;
import com.businessbuddy.security.BusinessContext;
import com.businessbuddy.service.costing.UnitConversionService;
import com.businessbuddy.service.inventory.InventoryService;
import com.businessbuddy.util.Money;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Purchases of ingredients and resale products (spec 8.6, pseudocode 4.6.4).
 *
 * <p>A purchase is entered as a DRAFT and changes stock only when it is received. Receiving
 * is idempotent: a second request finds the RECEIVED status and changes nothing, and the
 * unique index uq_movement_source rejects a duplicate movement as a last barrier.</p>
 */
@Service
@PreAuthorize("hasAnyRole('OWNER','MANAGER')")
public class PurchaseService {

    private final PurchaseRepository purchases;
    private final InventoryMovementRepository movements;
    private final CatalogService catalog;
    private final InventoryService inventory;
    private final UnitConversionService units;
    private final NotificationService notifications;
    private final BusinessContext context;
    private final AuditService audit;
    private final BigDecimal costIncreaseThreshold;

    public PurchaseService(PurchaseRepository purchases, InventoryMovementRepository movements, CatalogService catalog,
                           InventoryService inventory, UnitConversionService units, NotificationService notifications,
                           BusinessContext context, AuditService audit,
                           @Value("${businessbuddy.inventory.cost-increase-threshold}") BigDecimal costIncreaseThreshold) {
        this.purchases = purchases;
        this.movements = movements;
        this.catalog = catalog;
        this.inventory = inventory;
        this.units = units;
        this.notifications = notifications;
        this.context = context;
        this.audit = audit;
        this.costIncreaseThreshold = costIncreaseThreshold;
    }

    @Transactional(readOnly = true)
    public Purchase purchase(Long id) {
        Purchase p = purchases.findByIdAndBusinessId(id, context.currentBusinessId())
                .orElseThrow(() -> new NotFoundException("Purchase"));
        p.getSupplier().getName();
        p.getItems().forEach(i -> i.stockItem().getName());
        return p;
    }

    /** Creates or updates a draft purchase and recalculates its totals. */
    @Transactional
    public Purchase saveDraft(Long id, PurchaseForm form) {
        Business business = context.currentBusiness();
        Purchase purchase = id == null ? new Purchase() : purchase(id);
        if (purchase.getStatus() != PurchaseStatus.DRAFT) {
            throw new BusinessRuleException("Only a draft purchase can be changed");
        }
        Supplier supplier = catalog.supplier(form.getSupplierId());
        if (!supplier.isActive()) {
            throw new BusinessRuleException("Choose an active supplier");
        }
        String reference = form.getReferenceNo().trim();
        boolean duplicate = id == null
                ? purchases.existsByBusinessIdAndSupplierIdAndReferenceNoIgnoreCase(business.getId(), supplier.getId(), reference)
                : purchases.existsByBusinessIdAndSupplierIdAndReferenceNoIgnoreCaseAndIdNot(business.getId(),
                        supplier.getId(), reference, id);
        if (duplicate) {
            throw new BusinessRuleException("This supplier already has a purchase with reference " + reference);
        }
        if (form.getPurchaseDate().isAfter(LocalDate.now(business.zoneId()))) {
            throw new BusinessRuleException("The purchase date cannot be in the future");
        }
        List<PurchaseLineForm> lines = form.getLines().stream().filter(l -> !l.isBlank()).toList();
        if (lines.isEmpty()) {
            throw new BusinessRuleException("Add at least one line");
        }

        if (id == null) {
            purchase.setBusiness(business);
            purchase.setCreatedBy(context.currentUserEntity());
            purchase.setCurrencyCode(business.getCurrencyCode());
        }
        purchase.setSupplier(supplier);
        purchase.setReferenceNo(reference);
        purchase.setPurchaseDate(form.getPurchaseDate());
        purchase.setNotes(BusinessService.blankToNull(form.getNotes()));
        purchase.getItems().clear();

        BigDecimal subtotal = BigDecimal.ZERO;
        Set<String> seen = new HashSet<>();
        for (PurchaseLineForm lf : lines) {
            if (!seen.add(lf.getItemKey())) {
                throw new BusinessRuleException("The same item appears on more than one line");
            }
            if (lf.getQuantity() == null || lf.getQuantity().signum() <= 0 || lf.getUnitCost() == null
                    || lf.getUnitCost().signum() < 0) {
                throw new BusinessRuleException("Every line needs a quantity above zero and a unit cost");
            }
            PurchaseItem item = new PurchaseItem();
            StockItem stockItem = resolveItem(lf.getItemKey());
            if (stockItem instanceof Ingredient ingredient) {
                item.setIngredient(ingredient);
            } else {
                item.setProduct((Product) stockItem);
            }
            UnitOfMeasure unit = lf.getUnitCode() == null || lf.getUnitCode().isBlank()
                    ? stockItem.getStockUnit() : catalog.unit(lf.getUnitCode());
            if (!units.compatible(unit, stockItem.getStockUnit())) {
                throw new BusinessRuleException("The unit for " + stockItem.getName() + " cannot be converted to "
                        + stockItem.getStockUnit().getName());
            }
            if (lf.getExpiryDate() != null && lf.getExpiryDate().isBefore(form.getPurchaseDate())) {
                throw new BusinessRuleException("The expiry date of " + stockItem.getName()
                        + " cannot be before the purchase date");
            }
            item.setQuantity(Money.quantity(lf.getQuantity()));
            item.setUnit(unit);
            item.setUnitCost(Money.unitCost(lf.getUnitCost()));
            item.setLineTotal(Money.money(item.getQuantity().multiply(item.getUnitCost())));
            item.setExpiryDate(lf.getExpiryDate());
            purchase.addItem(item);
            subtotal = subtotal.add(item.getLineTotal());
        }
        BigDecimal discount = Money.money(form.getDiscountAmount());
        if (discount.compareTo(subtotal) > 0) {
            throw new BusinessRuleException("The discount cannot be more than the subtotal");
        }
        purchase.setSubtotal(subtotal);
        purchase.setDiscountAmount(discount);
        purchase.setAdditionalCosts(Money.money(form.getAdditionalCosts()));
        purchase.setTotalAmount(subtotal.subtract(discount).add(purchase.getAdditionalCosts()));
        purchases.save(purchase);
        audit.record(id == null ? "PURCHASE_CREATED" : "PURCHASE_UPDATED", "PURCHASE", purchase.getId());
        return purchase;
    }

    /**
     * Receives a draft purchase: converts each line to the stock unit, shares discounts and
     * additional costs across lines by value to give a landed cost, and adds the stock once.
     */
    @Transactional
    public Purchase receive(Long id, LocalDate receivedDate) {
        Long businessId = context.currentBusinessId();
        Purchase purchase = purchases.lockById(id).filter(p -> p.owningBusinessId().equals(businessId))
                .orElseThrow(() -> new NotFoundException("Purchase"));
        if (purchase.getStatus() == PurchaseStatus.RECEIVED) {
            return purchase;                                   // already received: nothing changes
        }
        if (purchase.getStatus() != PurchaseStatus.DRAFT || purchase.getItems().isEmpty()) {
            throw new BusinessRuleException("Only a draft purchase with at least one line can be received");
        }
        Business business = purchase.getBusiness();
        LocalDate today = LocalDate.now(business.zoneId());
        if (receivedDate == null || receivedDate.isBefore(purchase.getPurchaseDate()) || receivedDate.isAfter(today)) {
            throw new BusinessRuleException("The received date must be between the purchase date and today");
        }
        BigDecimal adjustment = purchase.getAdditionalCosts().subtract(purchase.getDiscountAmount());
        for (PurchaseItem item : purchase.getItems()) {
            StockItem stock = item.getIngredient() != null ? inventory.lockIngredient(item.getIngredient().getId())
                    : inventory.lockProduct(item.getProduct().getId());
            if (stock instanceof Ingredient ingredient && ingredient.isExpiryTracked() && item.getExpiryDate() == null) {
                throw new BusinessRuleException("Enter the expiry date for " + ingredient.getName());
            }
            BigDecimal baseQty = units.toStockQuantity(item.getQuantity(), item.getUnit(), stock.getStockUnit());
            BigDecimal share = purchase.getSubtotal().signum() > 0
                    ? Money.divide(item.getLineTotal(), purchase.getSubtotal()).multiply(adjustment)
                    : BigDecimal.ZERO;
            BigDecimal landed = Money.unitCost(Money.divide(item.getLineTotal().add(share), baseQty));
            item.setBaseQuantity(baseQty);
            item.setLandedUnitCost(landed);
            BigDecimal previousCost = stock.getAverageUnitCost();

            inventory.apply(stock, new InventoryService.Change(MovementType.PURCHASE_RECEIPT, baseQty, landed,
                    SourceType.PURCHASE_ITEM, item.getId(), null, null,
                    receivedDate.atStartOfDay(business.zoneId()).toInstant(), context.currentUserEntity()));

            BigDecimal limit = previousCost.multiply(BigDecimal.ONE.add(costIncreaseThreshold));
            if (previousCost.signum() > 0 && landed.compareTo(limit) > 0) {
                notifications.raise(business, NotificationType.COST_INCREASE, NotificationNature.OBSERVED,
                        stock.stockItemType(), stock.getId(), "Purchase cost of " + stock.getName() + " rose from "
                                + previousCost.stripTrailingZeros().toPlainString() + " to "
                                + landed.stripTrailingZeros().toPlainString() + " per " + stock.getStockUnit().getCode());
            }
        }
        purchase.setStatus(PurchaseStatus.RECEIVED);
        purchase.setReceivedDate(receivedDate);
        purchase.setReceivedBy(context.currentUserEntity());
        audit.record("PURCHASE_RECEIVED", "PURCHASE", purchase.getId(),
                Map.of("total", purchase.getTotalAmount().toPlainString()));
        return purchase;
    }

    /**
     * Cancels a purchase. A draft is simply marked CANCELLED. A received purchase is reversed
     * line by line at its landed cost, so the ledger keeps both the receipt and its reversal.
     */
    @Transactional
    public Purchase cancel(Long id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("Enter a reason for the cancellation");
        }
        Long businessId = context.currentBusinessId();
        Purchase purchase = purchases.lockById(id).filter(p -> p.owningBusinessId().equals(businessId))
                .orElseThrow(() -> new NotFoundException("Purchase"));
        if (purchase.getStatus() == PurchaseStatus.CANCELLED) {
            return purchase;
        }
        if (purchase.getStatus() == PurchaseStatus.RECEIVED) {
            for (PurchaseItem item : purchase.getItems()) {
                InventoryMovement original = movements.findByTypeAndSourceTypeAndSourceId(
                        MovementType.PURCHASE_RECEIPT, SourceType.PURCHASE_ITEM, item.getId())
                        .orElseThrow(() -> new BusinessRuleException("The receipt movement could not be found"));
                StockItem stock = item.getIngredient() != null ? inventory.lockIngredient(item.getIngredient().getId())
                        : inventory.lockProduct(item.getProduct().getId());
                inventory.apply(stock, new InventoryService.Change(MovementType.REVERSAL,
                        original.getQuantityChange().negate(), original.getUnitCost(), SourceType.PURCHASE_ITEM,
                        item.getId(), original, reason.trim(), null, context.currentUserEntity()));
            }
        }
        purchase.setStatus(PurchaseStatus.CANCELLED);
        String notes = purchase.getNotes() == null ? "" : purchase.getNotes() + "\n";
        purchase.setNotes(notes + "Cancelled: " + reason.trim());
        audit.record("PURCHASE_CANCELLED", "PURCHASE", purchase.getId(), Map.of("reason", reason.trim()));
        return purchase;
    }

    /** Resolves "I:12" or "P:5" to an ingredient or a resale product of the current business. */
    public StockItem resolveItem(String key) {
        if (key == null || key.length() < 3 || key.charAt(1) != ':') {
            throw new BusinessRuleException("Choose an item for every line");
        }
        long itemId;
        try {
            itemId = Long.parseLong(key.substring(2));
        } catch (NumberFormatException e) {
            throw new BusinessRuleException("Choose an item for every line");
        }
        if (key.charAt(0) == 'I') {
            return catalog.ingredient(itemId);
        }
        if (key.charAt(0) == 'P') {
            Product product = catalog.product(itemId);
            if (product.getType() != ProductType.RESALE) {
                throw new BusinessRuleException(product.getName() + " is made in-house; only resale products are purchased");
            }
            return product;
        }
        throw new BusinessRuleException("Choose an item for every line");
    }
}
