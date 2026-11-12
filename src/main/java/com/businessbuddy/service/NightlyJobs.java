package com.businessbuddy.service;

import com.businessbuddy.audit.AuditService;
import com.businessbuddy.entity.Business;
import com.businessbuddy.entity.Enums.NotificationNature;
import com.businessbuddy.entity.Enums.NotificationType;
import com.businessbuddy.entity.Ingredient;
import com.businessbuddy.entity.Product;
import com.businessbuddy.entity.PurchaseItem;
import com.businessbuddy.repository.BusinessRepository;
import com.businessbuddy.repository.IngredientRepository;
import com.businessbuddy.repository.ProductRepository;
import com.businessbuddy.repository.PurchaseItemRepository;
import com.businessbuddy.service.inventory.InventoryService;
import com.businessbuddy.service.inventory.InventoryService.Reconciliation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Jobs that run once a night for every business, outside trading hours (4.4.4, 4.6.10):
 * low-stock checks, ledger reconciliation, expiry alerts, demand forecasts and stockout
 * estimates. They can also be started from the Forecasts page.
 */
@Service
public class NightlyJobs {

    private static final Logger log = LoggerFactory.getLogger(NightlyJobs.class);

    private final BusinessRepository businesses;
    private final IngredientRepository ingredients;
    private final ProductRepository products;
    private final PurchaseItemRepository purchaseItems;
    private final InventoryService inventory;
    private final NotificationService notifications;
    private final ForecastService forecasts;
    private final AuditService audit;
    private final Clock clock;
    private final int expiryWarningDays;

    public NightlyJobs(BusinessRepository businesses, IngredientRepository ingredients, ProductRepository products,
                       PurchaseItemRepository purchaseItems, InventoryService inventory,
                       NotificationService notifications, ForecastService forecasts, AuditService audit, Clock clock,
                       @Value("${businessbuddy.inventory.expiry-warning-days}") int expiryWarningDays) {
        this.businesses = businesses;
        this.ingredients = ingredients;
        this.products = products;
        this.purchaseItems = purchaseItems;
        this.inventory = inventory;
        this.notifications = notifications;
        this.forecasts = forecasts;
        this.audit = audit;
        this.clock = clock;
        this.expiryWarningDays = expiryWarningDays;
    }

    @Scheduled(cron = "${businessbuddy.jobs.nightly-cron}")
    public void runNightly() {
        for (Business business : businesses.findAll()) {
            try {
                stockJob(business);
                forecasts.runAll(business);
            } catch (RuntimeException e) {
                log.error("Nightly job failed for business {}", business.getId(), e);
            }
        }
    }

    /** NightlyStockJob in 4.6.10. Returns the number of reconciliation mismatches found. */
    @Transactional
    public int stockJob(Business business) {
        for (Ingredient i : ingredients.findByBusinessIdAndActiveTrueOrderByName(business.getId())) {
            notifications.checkLowStock(i);
        }
        for (Product p : products.findByBusinessIdAndActiveTrueOrderByName(business.getId())) {
            notifications.checkLowStock(p);
        }
        int mismatches = 0;
        for (Reconciliation r : inventory.reconcile(business.getId())) {
            if (!r.matches()) {
                mismatches++;
                audit.recordFor("STOCK_MISMATCH", null, business.getId(), r.itemType(), r.itemId(),
                        Map.of("cached", r.cached().toPlainString(), "ledger", r.ledger().toPlainString()));
                notifications.raise(business, NotificationType.ADMIN, NotificationNature.OBSERVED, r.itemType(),
                        r.itemId(), "Stock record needs checking for " + r.name());
            }
        }
        LocalDate today = LocalDate.now(clock.withZone(business.zoneId()));
        Set<Long> expired = new HashSet<>();
        for (PurchaseItem item : purchaseItems.expiryCandidates(business.getId())) {
            Ingredient ingredient = item.getIngredient();
            if (item.getExpiryDate().isBefore(today)) {
                expired.add(ingredient.getId());
                notifications.raise(business, NotificationType.EXPIRED, NotificationNature.OBSERVED, "INGREDIENT",
                        ingredient.getId(), "A delivery of " + ingredient.getName() + " expired on " + item.getExpiryDate());
            } else if (!item.getExpiryDate().isAfter(today.plusDays(expiryWarningDays)) && !expired.contains(ingredient.getId())) {
                notifications.raise(business, NotificationType.EXPIRING_SOON, NotificationNature.OBSERVED, "INGREDIENT",
                        ingredient.getId(), "A delivery of " + ingredient.getName() + " expires on " + item.getExpiryDate());
            }
        }
        return mismatches;
    }
}
