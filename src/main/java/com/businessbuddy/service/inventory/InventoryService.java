package com.businessbuddy.service.inventory;

import com.businessbuddy.audit.AuditService;
import com.businessbuddy.entity.Business;
import com.businessbuddy.entity.Enums.MovementType;
import com.businessbuddy.entity.Enums.Role;
import com.businessbuddy.entity.Enums.SourceType;
import com.businessbuddy.entity.Ingredient;
import com.businessbuddy.entity.InventoryMovement;
import com.businessbuddy.entity.Product;
import com.businessbuddy.entity.StockItem;
import com.businessbuddy.entity.User;
import com.businessbuddy.exception.BusinessRuleException;
import com.businessbuddy.exception.NotFoundException;
import com.businessbuddy.repository.IngredientRepository;
import com.businessbuddy.repository.InventoryMovementRepository;
import com.businessbuddy.repository.ProductRepository;
import com.businessbuddy.security.BusinessContext;
import com.businessbuddy.security.CurrentUser;
import com.businessbuddy.service.NotificationService;
import com.businessbuddy.util.Money;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The single gateway for stock changes (spec 8.7, decision D4).
 *
 * <p>Every change writes one {@link InventoryMovement} and updates the cached quantity and
 * weighted-average cost of the item in the same transaction, while the item row is locked.
 * Callers are the purchase, production and sales services and the manual stock form.</p>
 */
@Service
public class InventoryService {

    private final InventoryMovementRepository movements;
    private final IngredientRepository ingredients;
    private final ProductRepository products;
    private final NotificationService notifications;
    private final AuditService audit;
    private final BusinessContext context;
    private final Clock clock;

    public InventoryService(InventoryMovementRepository movements, IngredientRepository ingredients,
                            ProductRepository products, NotificationService notifications, AuditService audit,
                            BusinessContext context, Clock clock) {
        this.movements = movements;
        this.ingredients = ingredients;
        this.products = products;
        this.notifications = notifications;
        this.audit = audit;
        this.context = context;
        this.clock = clock;
    }

    /** Locks an ingredient row for the rest of the transaction. */
    public Ingredient lockIngredient(Long id) {
        return ingredients.lockById(id).orElseThrow(() -> new NotFoundException("Ingredient"));
    }

    public Product lockProduct(Long id) {
        return products.lockById(id).orElseThrow(() -> new NotFoundException("Product"));
    }

    /** Describes one stock change. Quantity is signed and in the item's stock unit. */
    public record Change(MovementType type, BigDecimal quantity, BigDecimal unitCost, SourceType sourceType,
                         Long sourceId, InventoryMovement reversed, String reason, Instant date, User user) {
    }

    /**
     * Applies one change to a locked item.
     * <ul>
     *   <li>Increases update the weighted-average cost with the given unit cost (D3).</li>
     *   <li>Decreases are valued at the current average cost; the average does not change.</li>
     *   <li>Stock may not fall below zero unless the business allows it; then it is audited.</li>
     * </ul>
     */
    @Transactional
    public InventoryMovement apply(StockItem item, Change change) {
        BigDecimal qty = Money.quantity(change.quantity());
        if (qty.signum() == 0) {
            throw new BusinessRuleException("Quantity cannot be zero");
        }
        Business business = item.getBusiness();
        BigDecimal newQty = item.getCurrentQuantity().add(qty);
        BigDecimal unitCost;
        if (qty.signum() > 0) {
            unitCost = Money.unitCost(change.unitCost() == null ? item.getAverageUnitCost() : change.unitCost());
            item.setAverageUnitCost(Money.weightedAverage(item.getCurrentQuantity(), item.getAverageUnitCost(),
                    qty, unitCost));
        } else {
            if (newQty.signum() < 0 && !business.isAllowNegativeStock()) {
                throw new BusinessRuleException("Not enough " + item.getName() + ": need "
                        + plain(qty.negate()) + ", in stock " + plain(item.getCurrentQuantity()));
            }
            unitCost = Money.unitCost(change.unitCost() == null ? item.getAverageUnitCost() : change.unitCost());
        }
        item.setCurrentQuantity(newQty);

        InventoryMovement m = new InventoryMovement();
        m.setBusiness(business);
        if (item instanceof Ingredient ingredient) {
            m.setIngredient(ingredient);
        } else {
            m.setProduct((Product) item);
        }
        m.setType(change.type());
        m.setQuantityChange(qty);
        m.setUnitCost(unitCost);
        m.setSourceType(change.sourceType());
        m.setSourceId(change.sourceId());
        m.setReversedMovement(change.reversed());
        m.setReason(change.reason());
        m.setMovementDate(change.date() == null ? Instant.now(clock) : change.date());
        m.setCreatedBy(change.user());
        movements.save(m);

        if (newQty.signum() < 0) {
            audit.record("NEGATIVE_STOCK", item.stockItemType(), item.getId(),
                    Map.of("quantity", newQty.toPlainString()));
        }
        notifications.checkLowStock(item);
        return m;
    }

    /** Form input for a manual stock change (Table 4.46). */
    public record ManualChange(String itemType, Long itemId, MovementType type, BigDecimal quantity,
                               BigDecimal unitCost, String reason) {
    }

    /**
     * Records waste, an adjustment or a return entered by a user (pseudocode RecordStockChange).
     * Employees may only record waste; adjustments and returns need an Owner or Manager.
     */
    @Transactional
    public InventoryMovement recordManualChange(ManualChange input) {
        CurrentUser user = context.currentUser();
        MovementType type = input.type();
        if (type != MovementType.WASTE && type != MovementType.ADJUSTMENT && type != MovementType.RETURN) {
            throw new BusinessRuleException("Choose waste, adjustment or return");
        }
        if (type != MovementType.WASTE && !user.hasRole(Role.OWNER, Role.MANAGER)) {
            throw new AccessDeniedException("Only Owners and Managers may adjust stock");
        }
        if (input.quantity() == null || input.quantity().signum() == 0) {
            throw new BusinessRuleException("Enter a quantity other than zero");
        }
        if (input.reason() == null || input.reason().trim().length() < 3) {
            throw new BusinessRuleException("Enter a reason of at least 3 characters");
        }
        BigDecimal quantity = input.quantity();
        if (type == MovementType.WASTE) {
            quantity = quantity.abs().negate();          // waste always reduces stock
        }
        StockItem item = lockOwned(input.itemType(), input.itemId());
        InventoryMovement m = apply(item, new Change(type, quantity,
                quantity.signum() > 0 ? input.unitCost() : null, SourceType.MANUAL, null, null,
                input.reason().trim(), null, context.currentUserEntity()));
        audit.record("STOCK_" + type.name(), item.stockItemType(), item.getId(),
                Map.of("quantity", m.getQuantityChange().toPlainString(), "reason", m.getReason()));
        return m;
    }

    /** Locks an ingredient or product after checking it belongs to the current business. */
    public StockItem lockOwned(String itemType, Long itemId) {
        Long businessId = context.currentBusinessId();
        StockItem item = "PRODUCT".equalsIgnoreCase(itemType) ? lockProduct(itemId) : lockIngredient(itemId);
        if (!Objects.equals(item.getBusiness().getId(), businessId)) {
            throw new NotFoundException("Item");
        }
        return item;
    }

    /** One line of the reconciliation report: cached quantity against the ledger total. */
    public record Reconciliation(String itemType, Long itemId, String name, BigDecimal cached, BigDecimal ledger) {
        public boolean matches() {
            return cached.compareTo(ledger) == 0;
        }
    }

    /** Compares every item's cached stock with the sum of its ledger (Table 4.36). */
    @Transactional(readOnly = true)
    public List<Reconciliation> reconcile(Long businessId) {
        List<Reconciliation> result = new ArrayList<>();
        for (Ingredient i : ingredients.findByBusinessIdOrderByName(businessId)) {
            result.add(new Reconciliation("INGREDIENT", i.getId(), i.getName(), i.getCurrentQuantity(),
                    movements.ledgerTotalForIngredient(i.getId())));
        }
        for (Product p : products.findByBusinessIdOrderByName(businessId)) {
            result.add(new Reconciliation("PRODUCT", p.getId(), p.getName(), p.getCurrentQuantity(),
                    movements.ledgerTotalForProduct(p.getId())));
        }
        return result;
    }

    /** Inventory value per item and in total, at weighted-average cost. */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> inventoryValue(Long businessId) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        BigDecimal ingredientValue = ingredients.findByBusinessIdOrderByName(businessId).stream()
                .map(StockItem::stockValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal productValue = products.findByBusinessIdOrderByName(businessId).stream()
                .map(StockItem::stockValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        totals.put("ingredients", ingredientValue);
        totals.put("products", productValue);
        totals.put("total", ingredientValue.add(productValue));
        return totals;
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
