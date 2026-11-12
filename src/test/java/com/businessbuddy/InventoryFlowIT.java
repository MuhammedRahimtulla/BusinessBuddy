package com.businessbuddy;

import com.businessbuddy.dto.TransactionForms.ProductionForm;
import com.businessbuddy.dto.TransactionForms.SaleForm;
import com.businessbuddy.dto.TransactionForms.SaleLineForm;
import com.businessbuddy.entity.Enums.MovementType;
import com.businessbuddy.entity.Enums.NotificationStatus;
import com.businessbuddy.entity.Enums.NotificationType;
import com.businessbuddy.entity.Enums.PaymentMethod;
import com.businessbuddy.entity.Enums.ProductType;
import com.businessbuddy.entity.Enums.PurchaseStatus;
import com.businessbuddy.entity.Enums.Role;
import com.businessbuddy.entity.Enums.SaleStatus;
import com.businessbuddy.entity.Enums.SourceType;
import com.businessbuddy.entity.Ingredient;
import com.businessbuddy.entity.Product;
import com.businessbuddy.entity.ProductionBatch;
import com.businessbuddy.entity.Purchase;
import com.businessbuddy.entity.PurchaseItem;
import com.businessbuddy.entity.Sale;
import com.businessbuddy.entity.SaleItem;
import com.businessbuddy.entity.User;
import com.businessbuddy.exception.BusinessRuleException;
import com.businessbuddy.exception.NotFoundException;
import com.businessbuddy.repository.IngredientRepository;
import com.businessbuddy.repository.InventoryMovementRepository;
import com.businessbuddy.repository.NotificationRepository;
import com.businessbuddy.repository.ProductRepository;
import com.businessbuddy.repository.PurchaseRepository;
import com.businessbuddy.repository.SaleRepository;
import com.businessbuddy.service.ProductionService;
import com.businessbuddy.service.SalesService;
import com.businessbuddy.service.inventory.InventoryService;
import com.businessbuddy.service.inventory.InventoryService.ManualChange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests of the inventory ledger through purchases, production and sales
 * (FR-06, FR-07, FR-10, FR-13; spec 16 "Inventory tests"). Every expected figure is worked
 * out by hand in the comments.
 *
 * <p>Scenario: flour 10 kg at 20.00/kg (200.00) and sugar 5 kg at 30.00/kg (150.00) with a
 * 35.00 delivery charge. The charge is shared by value: flour 20.00, sugar 15.00, so the
 * landed cost is flour 220.00 / 10 000 g = 0.0220 and sugar 165.00 / 5 000 g = 0.0330.
 * A cake batch of 10 uses 1 kg flour and 500 g sugar: 22.00 + 16.50 = 38.50, so 3.85 a cake.</p>
 */
class InventoryFlowIT extends IntegrationTestBase {

    @Autowired ProductionService production;
    @Autowired SalesService sales;
    @Autowired InventoryService inventory;
    @Autowired IngredientRepository ingredients;
    @Autowired ProductRepository products;
    @Autowired PurchaseRepository purchaseRepo;
    @Autowired SaleRepository saleRepo;
    @Autowired InventoryMovementRepository movements;
    @Autowired NotificationRepository notifications;
    @Autowired TransactionTemplate tx;

    User owner;
    Long supplier;
    Long flour;
    Long sugar;
    Long cake;
    Long purchaseId;

    @BeforeEach
    void setUp() {
        owner = newBusinessOwner("Inventory flow");
        setTaxRate(owner, "0.15");
        supplier = supplier("Mill");
        flour = ingredient("Flour", "g", "1000");
        sugar = ingredient("Sugar", "g", "0");
        cake = product("Cake", ProductType.MANUFACTURED, "40.00");
        recipe(cake, "10", List.of(recipeLine(flour, "1", "kg"), recipeLine(sugar, "500", "g")));
        purchaseId = receivedPurchase(supplier, "35.00",
                purchaseLine("I:" + flour, "10", "kg", "20.00"), purchaseLine("I:" + sugar, "5", "kg", "30.00"));
    }

    private Ingredient flour() { return ingredients.findById(flour).orElseThrow(); }
    private Product cake() { return products.findById(cake).orElseThrow(); }

    private ProductionBatch produce(String planned, String actual) {
        ProductionForm f = new ProductionForm();
        f.setProductId(cake);
        f.setPlannedOutput(new BigDecimal(planned));
        f.setProductionDate(LocalDate.now().minusDays(1));
        Long id = production.createDraft(f).getId();
        return production.confirm(id, new BigDecimal(actual), null, null);
    }

    private Sale draftSale(String qty) {
        SaleForm f = new SaleForm();
        f.setPaymentMethod(PaymentMethod.CASH);
        f.getLines().add(new SaleLineForm(cake, new BigDecimal(qty), null, BigDecimal.ZERO));
        return sales.saveDraft(null, f);
    }

    @Test
    @DisplayName("TC-INV-01: receiving a purchase adds stock once at landed cost; receiving again changes nothing")
    void purchaseReceivedOnce() {
        assertThat(flour().getCurrentQuantity()).isEqualByComparingTo("10000");
        assertThat(flour().getAverageUnitCost()).isEqualByComparingTo("0.0220");
        assertThat(ingredients.findById(sugar).orElseThrow().getAverageUnitCost()).isEqualByComparingTo("0.0330");

        purchases.receive(purchaseId, LocalDate.now().minusDays(1));          // repeated request

        assertThat(flour().getCurrentQuantity()).isEqualByComparingTo("10000");
        Purchase p = tx.execute(s -> {
            Purchase loaded = purchaseRepo.findById(purchaseId).orElseThrow();
            loaded.getItems().size();
            return loaded;
        });
        assertThat(p.getStatus()).isEqualTo(PurchaseStatus.RECEIVED);
        assertThat(p.getTotalAmount()).isEqualByComparingTo("385.00");
        for (PurchaseItem item : p.getItems()) {
            assertThat(movements.findByTypeAndSourceTypeAndSourceId(MovementType.PURCHASE_RECEIPT,
                    SourceType.PURCHASE_ITEM, item.getId())).isPresent();
        }
        assertThat(movements.ledgerTotalForIngredient(flour)).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("TC-INV-02: confirming production consumes ingredients and adds finished stock at unit cost 3.85")
    void productionConsumesAndProduces() {
        ProductionBatch batch = produce("10", "10");
        assertThat(batch.getTotalCost()).isEqualByComparingTo("38.50");
        assertThat(batch.getUnitCost()).isEqualByComparingTo("3.8500");
        assertThat(flour().getCurrentQuantity()).isEqualByComparingTo("9000");
        assertThat(ingredients.findById(sugar).orElseThrow().getCurrentQuantity()).isEqualByComparingTo("4500");
        assertThat(cake().getCurrentQuantity()).isEqualByComparingTo("10");
        assertThat(cake().getAverageUnitCost()).isEqualByComparingTo("3.8500");
        // confirming the same batch again changes nothing
        production.confirm(batch.getId(), BigDecimal.TEN, null, null);
        assertThat(flour().getCurrentQuantity()).isEqualByComparingTo("9000");
    }

    @Test
    @DisplayName("TC-INV-03: a sale reduces finished stock once, stores price and cost snapshots, and confirming twice changes nothing")
    void saleReducesStockOnce() {
        produce("10", "10");
        Sale sale = draftSale("4");
        assertThat(sale.getSubtotal()).isEqualByComparingTo("160.00");
        assertThat(sale.getTaxTotal()).isEqualByComparingTo("24.00");
        assertThat(sale.getTotal()).isEqualByComparingTo("184.00");
        sales.confirm(sale.getId(), PaymentMethod.CARD);
        sales.confirm(sale.getId(), PaymentMethod.CARD);
        assertThat(cake().getCurrentQuantity()).isEqualByComparingTo("6");
        assertThat(flour().getCurrentQuantity()).isEqualByComparingTo("9000");   // ingredients not consumed again
        SaleItem item = tx.execute(s -> saleRepo.findById(sale.getId()).orElseThrow().getItems().get(0));
        assertThat(item.getUnitCostSnapshot()).isEqualByComparingTo("3.8500");
        assertThat(item.getLineCost()).isEqualByComparingTo("15.40");
    }

    @Test
    @DisplayName("TC-INV-04: voiding a sale returns the stock at the original cost and is idempotent")
    void voidRestoresStock() {
        produce("10", "10");
        Sale sale = draftSale("4");
        sales.confirm(sale.getId(), PaymentMethod.CASH);
        sales.voidSale(sale.getId(), "Customer returned the order");
        sales.voidSale(sale.getId(), "Customer returned the order");
        assertThat(cake().getCurrentQuantity()).isEqualByComparingTo("10");
        assertThat(cake().getAverageUnitCost()).isEqualByComparingTo("3.8500");
        assertThat(saleRepo.findById(sale.getId()).orElseThrow().getStatus()).isEqualTo(SaleStatus.VOIDED);
        assertThat(movements.ledgerTotalForProduct(cake)).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("TC-INV-05: a sale larger than stock is refused and nothing is changed (negative stock prevented)")
    void negativeStockPrevented() {
        produce("10", "10");
        Sale sale = draftSale("11");
        assertThatThrownBy(() -> sales.confirm(sale.getId(), PaymentMethod.CASH))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Not enough Cake");
        assertThat(cake().getCurrentQuantity()).isEqualByComparingTo("10");
        assertThat(saleRepo.findById(sale.getId()).orElseThrow().getStatus()).isEqualTo(SaleStatus.DRAFT);
    }

    @Test
    @DisplayName("TC-INV-06: waste always reduces stock; an Employee may record waste but not adjustments")
    void wasteAndRoles() {
        inventory.recordManualChange(new ManualChange("INGREDIENT", flour, MovementType.WASTE,
                new BigDecimal("250"), null, "Bag torn"));              // positive input is treated as a reduction
        assertThat(flour().getCurrentQuantity()).isEqualByComparingTo("9750");

        User employee = addStaff(Role.EMPLOYEE);
        loginAs(employee);
        inventory.recordManualChange(new ManualChange("INGREDIENT", flour, MovementType.WASTE,
                new BigDecimal("-50"), null, "Spilled"));
        assertThat(flour().getCurrentQuantity()).isEqualByComparingTo("9700");
        assertThatThrownBy(() -> inventory.recordManualChange(new ManualChange("INGREDIENT", flour,
                MovementType.ADJUSTMENT, new BigDecimal("100"), null, "Found a bag")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("TC-INV-07: after purchases, production, sales, voids and waste the stock reconciles with the ledger")
    void reconciliation() {
        produce("20", "19");
        Sale s1 = draftSale("5");
        sales.confirm(s1.getId(), PaymentMethod.CASH);
        Sale s2 = draftSale("3");
        sales.confirm(s2.getId(), PaymentMethod.CARD);
        sales.voidSale(s2.getId(), "Wrong item");
        inventory.recordManualChange(new ManualChange("INGREDIENT", sugar, MovementType.WASTE,
                new BigDecimal("10"), null, "Damp"));
        assertThat(inventory.reconcile(owner.owningBusinessId())).allSatisfy(r -> assertThat(r.matches()).isTrue());
        assertThat(cake().getCurrentQuantity()).isEqualByComparingTo("14");      // 19 - 5
    }

    @Test
    @DisplayName("TC-INV-08: low stock raises one observed alert and resolves it when stock is replenished")
    void lowStockNotification() {
        inventory.recordManualChange(new ManualChange("INGREDIENT", flour, MovementType.ADJUSTMENT,
                new BigDecimal("-9500"), null, "Stock count"));            // 500 g left, minimum 1000 g
        inventory.recordManualChange(new ManualChange("INGREDIENT", flour, MovementType.WASTE,
                new BigDecimal("100"), null, "Spilled"));                  // still low: no second alert
        var open = notifications.findUnresolved(owner.owningBusinessId(), NotificationType.LOW_STOCK, "INGREDIENT", flour);
        assertThat(open).isPresent();
        // one alert only: it was raised at 500 g and not duplicated when stock fell to 400 g
        assertThat(open.get().getMessage()).startsWith("Current stock of Flour is 500 g");
        assertThat(notifications.findAll().stream().filter(n -> n.getType() == NotificationType.LOW_STOCK
                && flour.equals(n.getRelatedId())).count()).isEqualTo(1);

        receivedPurchase(supplier, "0", purchaseLine("I:" + flour, "5", "kg", "21.00"));
        assertThat(notifications.findUnresolved(owner.owningBusinessId(), NotificationType.LOW_STOCK, "INGREDIENT", flour)).isEmpty();
        assertThat(notifications.findById(open.get().getId()).orElseThrow().getStatus()).isEqualTo(NotificationStatus.RESOLVED);
    }

    @Test
    @DisplayName("TC-INV-09: cancelling a received purchase reverses its stock at the landed cost")
    void cancelReceivedPurchase() {
        purchases.cancel(purchaseId, "Wrong delivery");
        assertThat(flour().getCurrentQuantity()).isEqualByComparingTo("0");
        assertThat(purchaseRepo.findById(purchaseId).orElseThrow().getStatus()).isEqualTo(PurchaseStatus.CANCELLED);
        assertThat(movements.ledgerTotalForIngredient(flour)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("TC-INV-10: two concurrent confirmations of the same sale reduce stock only once")
    void concurrentConfirmation() throws Exception {
        produce("10", "10");
        Long saleId = draftSale("3").getId();
        SecurityContext context = SecurityContextHolder.getContext();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<String> task = () -> {
            SecurityContextHolder.setContext(context);
            try {
                sales.confirm(saleId, PaymentMethod.CASH);
                return "ok";
            } catch (RuntimeException e) {
                return e.getClass().getSimpleName();
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
        List<Future<String>> results = new ArrayList<>();
        results.add(pool.submit(task));
        results.add(pool.submit(task));
        for (Future<String> f : results) {
            f.get();
        }
        pool.shutdown();
        assertThat(cake().getCurrentQuantity()).isEqualByComparingTo("7");
        assertThat(movements.ledgerTotalForProduct(cake)).isEqualByComparingTo("7");
    }

    @Test
    @DisplayName("TC-INV-11: later price rises do not change the cost stored on an earlier sale (historical snapshot)")
    void historicalSnapshot() {
        produce("10", "10");
        Sale first = draftSale("2");
        sales.confirm(first.getId(), PaymentMethod.CASH);
        receivedPurchase(supplier, "0", purchaseLine("I:" + flour, "10", "kg", "60.00"));   // flour triples in price
        produce("10", "10");
        assertThat(cake().getAverageUnitCost()).isGreaterThan(new BigDecimal("3.85"));
        SaleItem item = tx.execute(s -> saleRepo.findById(first.getId()).orElseThrow().getItems().get(0));
        assertThat(item.getLineCost()).isEqualByComparingTo("7.70");                     // still 2 x 3.85
    }

    @Test
    @DisplayName("TC-SEC-10: a user cannot load another business's product, even with its identifier")
    void crossBusinessIsolation() {
        Long otherProduct = cake;
        newBusinessOwner("Other business");
        assertThatThrownBy(() -> catalog.product(otherProduct)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> sales.sale(draftSaleIdOf(otherProduct))).isInstanceOf(NotFoundException.class);
    }

    private Long draftSaleIdOf(Long productId) {
        return saleRepo.findAll().stream().map(Sale::getId).findFirst().orElse(-1L);
    }
}
