package com.businessbuddy.service;

import com.businessbuddy.audit.AuditService;
import com.businessbuddy.dto.TransactionForms.SaleForm;
import com.businessbuddy.dto.TransactionForms.SaleLineForm;
import com.businessbuddy.entity.Business;
import com.businessbuddy.entity.Enums.MovementType;
import com.businessbuddy.entity.Enums.PaymentMethod;
import com.businessbuddy.entity.Enums.Role;
import com.businessbuddy.entity.Enums.SaleStatus;
import com.businessbuddy.entity.Enums.SourceType;
import com.businessbuddy.entity.InventoryMovement;
import com.businessbuddy.entity.Product;
import com.businessbuddy.entity.Sale;
import com.businessbuddy.entity.SaleItem;
import com.businessbuddy.exception.BusinessRuleException;
import com.businessbuddy.exception.NotFoundException;
import com.businessbuddy.repository.InventoryMovementRepository;
import com.businessbuddy.repository.SaleRepository;
import com.businessbuddy.security.BusinessContext;
import com.businessbuddy.security.CurrentUser;
import com.businessbuddy.service.inventory.InventoryService;
import com.businessbuddy.util.Money;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Sales (spec 8.14, pseudocode 4.6.8 and 4.6.9).
 *
 * <p>A sale is saved as a DRAFT with its price and tax rate frozen on each line. Confirming
 * reduces finished stock once and stores the cost snapshot used for cost of goods sold.
 * A confirmed sale is never deleted: it is voided with a reason and its stock is returned
 * by REVERSAL movements at the original cost.</p>
 */
@Service
public class SalesService {

    private final SaleRepository sales;
    private final InventoryMovementRepository movements;
    private final CatalogService catalog;
    private final InventoryService inventory;
    private final BusinessContext context;
    private final AuditService audit;
    private final Clock clock;

    public SalesService(SaleRepository sales, InventoryMovementRepository movements, CatalogService catalog,
                        InventoryService inventory, BusinessContext context, AuditService audit, Clock clock) {
        this.sales = sales;
        this.movements = movements;
        this.catalog = catalog;
        this.inventory = inventory;
        this.context = context;
        this.audit = audit;
        this.clock = clock;
    }

    /** Loads a sale of the current business. Employees may only open sales they recorded. */
    @Transactional(readOnly = true)
    public Sale sale(Long id) {
        Sale sale = sales.findByIdAndBusinessId(id, context.currentBusinessId())
                .orElseThrow(() -> new NotFoundException("Sale"));
        CurrentUser user = context.currentUser();
        if (user.getRole() == Role.EMPLOYEE && !Objects.equals(sale.getRecordedBy().getId(), user.getUserId())) {
            throw new NotFoundException("Sale");
        }
        sale.getItems().forEach(i -> i.getProduct().getName());
        if (sale.getCustomer() != null) {
            sale.getCustomer().getName();
        }
        return sale;
    }

    @Transactional
    public Sale saveDraft(Long id, SaleForm form) {
        return saveDraft(id, form, null);
    }

    /**
     * Creates or updates a draft. Line totals and header totals are calculated here every
     * time, because the database CHECK rules require consistent totals even on drafts.
     * {@code saleDate} may be given for back-dated entry (used by the demonstration data).
     */
    @Transactional
    public Sale saveDraft(Long id, SaleForm form, Instant saleDate) {
        Business business = context.currentBusiness();
        Sale sale = id == null ? new Sale() : sale(id);
        if (sale.getStatus() != SaleStatus.DRAFT) {
            throw new BusinessRuleException("Only a draft sale can be changed");
        }
        List<SaleLineForm> lines = form.getLines().stream().filter(l -> !l.isBlank()).toList();
        if (lines.isEmpty()) {
            throw new BusinessRuleException("Add at least one product");
        }
        if (id == null) {
            sale.setBusiness(business);
            sale.setRecordedBy(context.currentUserEntity());
            sale.setCurrencyCode(business.getCurrencyCode());
            sale.setSaleNumber(nextSaleNumber(business.getId()));
            sale.setSaleDate(saleDate == null ? Instant.now(clock) : saleDate);
        }
        sale.setCustomer(form.getCustomerId() == null ? null : catalog.customer(form.getCustomerId()));
        sale.setNotes(BusinessService.blankToNull(form.getNotes()));
        sale.setPaymentMethod(form.getPaymentMethod());
        sale.getItems().clear();

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discounts = BigDecimal.ZERO;
        BigDecimal taxes = BigDecimal.ZERO;
        for (SaleLineForm lf : lines) {
            Product product = catalog.product(lf.getProductId());
            if (!product.isActive()) {
                throw new BusinessRuleException(product.getName() + " is no longer sold");
            }
            if (lf.getQuantity() == null || lf.getQuantity().signum() <= 0) {
                throw new BusinessRuleException("Enter a quantity above zero for " + product.getName());
            }
            SaleItem item = new SaleItem();
            item.setProduct(product);
            item.setQuantity(Money.quantity(lf.getQuantity()));
            item.setUnitPrice(Money.money(lf.getUnitPrice() == null ? product.getSellingPrice() : lf.getUnitPrice()));
            item.setDiscountAmount(Money.money(Money.nz(lf.getDiscountAmount())));
            item.setTaxRate(business.getDefaultTaxRate());
            calculateLine(item);
            sale.addItem(item);
            subtotal = subtotal.add(Money.money(item.getQuantity().multiply(item.getUnitPrice())));
            discounts = discounts.add(item.getDiscountAmount());
            taxes = taxes.add(item.getTaxAmount());
        }
        sale.setSubtotal(subtotal);
        sale.setDiscountTotal(discounts);
        sale.setTaxTotal(taxes);
        sale.setTotal(subtotal.subtract(discounts).add(taxes));
        sales.save(sale);
        return sale;
    }

    /** net = round(qty x price) - discount; tax = round(net x rate); total = net + tax. */
    static void calculateLine(SaleItem item) {
        BigDecimal gross = Money.money(item.getQuantity().multiply(item.getUnitPrice()));
        if (item.getDiscountAmount().compareTo(gross) > 0) {
            throw new BusinessRuleException("Discount cannot exceed the line value");
        }
        BigDecimal net = gross.subtract(item.getDiscountAmount());
        BigDecimal tax = Money.money(net.multiply(item.getTaxRate()));
        item.setNetAmount(net);
        item.setTaxAmount(tax);
        item.setLineTotal(net.add(tax));
    }

    /** Confirms a draft sale (4.6.8): stock is reduced once and cost snapshots are stored. */
    @Transactional
    public Sale confirm(Long id, PaymentMethod paymentMethod) {
        Sale sale = lockOwned(id);
        if (sale.getStatus() == SaleStatus.CONFIRMED) {
            return sale;                                         // resubmission changes nothing
        }
        PaymentMethod method = paymentMethod != null ? paymentMethod : sale.getPaymentMethod();
        if (sale.getStatus() != SaleStatus.DRAFT || sale.getItems().isEmpty() || method == null) {
            throw new BusinessRuleException("A draft sale needs at least one line and a payment method");
        }
        for (SaleItem item : sale.getItems()) {
            Product product = inventory.lockProduct(item.getProduct().getId());
            if (!product.isActive()) {
                throw new BusinessRuleException(product.getName() + " is no longer sold");
            }
            BigDecimal unitCost = product.getAverageUnitCost();
            item.setUnitCostSnapshot(unitCost);
            item.setLineCost(Money.unitCost(item.getQuantity().multiply(unitCost)));
            if (item.getUnitPrice().compareTo(unitCost) < 0) {
                audit.record("SOLD_BELOW_COST", "SALE_ITEM", item.getId(), Map.of("product", product.getName(),
                        "price", item.getUnitPrice().toPlainString(), "unitCost", unitCost.toPlainString()));
            }
            inventory.apply(product, new InventoryService.Change(MovementType.SALE, item.getQuantity().negate(),
                    unitCost, SourceType.SALE_ITEM, item.getId(), null, null, sale.getSaleDate(),
                    context.currentUserEntity()));
        }
        sale.setPaymentMethod(method);
        sale.setStatus(SaleStatus.CONFIRMED);
        audit.record("SALE_CONFIRMED", "SALE", sale.getId(), Map.of("total", sale.getTotal().toPlainString()));
        return sale;
    }

    /** Voids a confirmed sale (4.6.9). Owners and Managers only. */
    @Transactional
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public Sale voidSale(Long id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("Enter a reason for the void");
        }
        Sale sale = lockOwned(id);
        if (sale.getStatus() == SaleStatus.VOIDED) {
            return sale;
        }
        if (sale.getStatus() != SaleStatus.CONFIRMED) {
            throw new BusinessRuleException("Only a confirmed sale can be voided. Delete a draft instead");
        }
        for (SaleItem item : sale.getItems()) {
            InventoryMovement original = movements.findByTypeAndSourceTypeAndSourceId(MovementType.SALE,
                    SourceType.SALE_ITEM, item.getId())
                    .orElseThrow(() -> new BusinessRuleException("The stock movement of this sale could not be found"));
            Product product = inventory.lockProduct(item.getProduct().getId());
            inventory.apply(product, new InventoryService.Change(MovementType.REVERSAL, item.getQuantity(),
                    original.getUnitCost(), SourceType.SALE_ITEM, item.getId(), original, reason.trim(), null,
                    context.currentUserEntity()));
        }
        sale.setStatus(SaleStatus.VOIDED);
        sale.setVoidedBy(context.currentUserEntity());
        sale.setVoidedAt(Instant.now(clock));
        sale.setVoidReason(reason.trim());
        audit.record("SALE_VOIDED", "SALE", sale.getId(), Map.of("reason", reason.trim(),
                "total", sale.getTotal().toPlainString()));
        return sale;
    }

    /** Deletes a draft. Confirmed and voided sales are financial records and are never deleted. */
    @Transactional
    public void deleteDraft(Long id) {
        Sale sale = sale(id);
        if (sale.getStatus() != SaleStatus.DRAFT) {
            throw new BusinessRuleException("Only a draft sale can be deleted");
        }
        sales.delete(sale);
    }

    private Sale lockOwned(Long id) {
        Long businessId = context.currentBusinessId();
        Sale sale = sales.lockById(id).filter(s -> s.owningBusinessId().equals(businessId))
                .orElseThrow(() -> new NotFoundException("Sale"));
        CurrentUser user = context.currentUser();
        if (user.getRole() == Role.EMPLOYEE && !Objects.equals(sale.getRecordedBy().getId(), user.getUserId())) {
            throw new NotFoundException("Sale");
        }
        return sale;
    }

    private String nextSaleNumber(Long businessId) {
        long next = sales.countByBusinessId(businessId) + 1;
        String number = String.format("S-%06d", next);
        while (sales.existsByBusinessIdAndSaleNumber(businessId, number)) {
            number = String.format("S-%06d", ++next);
        }
        return number;
    }
}
