package com.businessbuddy.service;

import com.businessbuddy.audit.AuditService;
import com.businessbuddy.dto.TransactionForms.ExpenseForm;
import com.businessbuddy.entity.Business;
import com.businessbuddy.entity.Expense;
import com.businessbuddy.entity.ExpenseCategory;
import com.businessbuddy.exception.BusinessRuleException;
import com.businessbuddy.exception.NotFoundException;
import com.businessbuddy.repository.ExpenseCategoryRepository;
import com.businessbuddy.repository.ExpenseRepository;
import com.businessbuddy.security.BusinessContext;
import com.businessbuddy.util.Money;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

/**
 * Operating expenses (spec 8.15). They are reported separately from product costs and are
 * never added to recipe costs, so nothing is counted twice in the net operating result.
 */
@Service
@PreAuthorize("hasAnyRole('OWNER','MANAGER')")
public class ExpenseService {

    private final ExpenseRepository expenses;
    private final ExpenseCategoryRepository categories;
    private final CatalogService catalog;
    private final BusinessContext context;
    private final AuditService audit;

    public ExpenseService(ExpenseRepository expenses, ExpenseCategoryRepository categories, CatalogService catalog,
                          BusinessContext context, AuditService audit) {
        this.expenses = expenses;
        this.categories = categories;
        this.catalog = catalog;
        this.context = context;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Expense expense(Long id) {
        return expenses.findByIdAndBusinessId(id, context.currentBusinessId())
                .orElseThrow(() -> new NotFoundException("Expense"));
    }

    @Transactional
    public Expense save(Long id, ExpenseForm form) {
        Business business = context.currentBusiness();
        if (form.getExpenseDate().isAfter(LocalDate.now(business.zoneId()))) {
            throw new BusinessRuleException("The expense date cannot be in the future");
        }
        ExpenseCategory category = categories.findByIdAndBusinessId(form.getCategoryId(), business.getId())
                .orElseThrow(() -> new NotFoundException("Expense category"));
        Expense e = id == null ? new Expense() : expense(id);
        if (id == null) {
            e.setBusiness(business);
            e.setRecordedBy(context.currentUserEntity());
            e.setCurrencyCode(business.getCurrencyCode());
        }
        String oldAmount = id == null ? null : e.getAmount().toPlainString();
        e.setCategory(category);
        e.setDescription(form.getDescription().trim());
        e.setAmount(Money.money(form.getAmount()));
        e.setExpenseDate(form.getExpenseDate());
        e.setSupplier(form.getSupplierId() == null ? null : catalog.supplier(form.getSupplierId()));
        e.setPaymentMethod(form.getPaymentMethod());
        e.setReferenceNo(BusinessService.blankToNull(form.getReferenceNo()));
        e.setNotes(BusinessService.blankToNull(form.getNotes()));
        expenses.save(e);
        audit.record(id == null ? "EXPENSE_RECORDED" : "EXPENSE_CHANGED", "EXPENSE", e.getId(),
                id == null ? Map.of("amount", e.getAmount().toPlainString())
                        : Map.of("amount", oldAmount + " -> " + e.getAmount().toPlainString()));
        return e;
    }
}
