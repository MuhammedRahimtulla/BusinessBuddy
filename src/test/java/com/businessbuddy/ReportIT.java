package com.businessbuddy;

import com.businessbuddy.dto.TransactionForms.ExpenseForm;
import com.businessbuddy.dto.TransactionForms.ProductionForm;
import com.businessbuddy.dto.TransactionForms.SaleForm;
import com.businessbuddy.dto.TransactionForms.SaleLineForm;
import com.businessbuddy.entity.Business;
import com.businessbuddy.entity.Enums.PaymentMethod;
import com.businessbuddy.entity.Enums.ProductType;
import com.businessbuddy.entity.Enums.Role;
import com.businessbuddy.entity.User;
import com.businessbuddy.repository.ExpenseCategoryRepository;
import com.businessbuddy.service.ExpenseService;
import com.businessbuddy.service.ProductionService;
import com.businessbuddy.service.SalesService;
import com.businessbuddy.service.reporting.ReportService;
import com.businessbuddy.service.reporting.ReportService.Profitability;
import com.businessbuddy.util.DateRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests of the profitability calculations and CSV export (FR-14, FR-15;
 * spec 16 "Sales and expense tests"). Expected values are worked out by hand.
 *
 * <p>Cake unit cost 3.85 (see InventoryFlowIT). Sale A: 4 cakes at 40.00 with a 10.00
 * discount: net 150.00, tax 22.50, cost 15.40. Sale B: 2 cakes at 40.00, voided.
 * Expense: rent 50.00.</p>
 */
class ReportIT extends IntegrationTestBase {

    @Autowired ProductionService production;
    @Autowired SalesService sales;
    @Autowired ExpenseService expenses;
    @Autowired ReportService reports;
    @Autowired ExpenseCategoryRepository expenseCategories;

    User owner;
    Business business;
    DateRange today;

    @BeforeEach
    void scenario() {
        owner = newBusinessOwner("Reports");
        setTaxRate(owner, "0.15");
        business = business(owner);
        Long supplier = supplier("Mill");
        Long flour = ingredient("Flour", "g", "0");
        Long sugar = ingredient("Sugar", "g", "0");
        Long cake = product("Cake", ProductType.MANUFACTURED, "40.00");
        recipe(cake, "10", List.of(recipeLine(flour, "1", "kg"), recipeLine(sugar, "500", "g")));
        receivedPurchase(supplier, "35.00", purchaseLine("I:" + flour, "10", "kg", "20.00"),
                purchaseLine("I:" + sugar, "5", "kg", "30.00"));
        ProductionForm pf = new ProductionForm();
        pf.setProductId(cake);
        pf.setPlannedOutput(BigDecimal.TEN);
        pf.setProductionDate(LocalDate.now().minusDays(1));
        production.confirm(production.createDraft(pf).getId(), BigDecimal.TEN, null, null);

        SaleForm a = new SaleForm();
        a.getLines().add(new SaleLineForm(cake, new BigDecimal("4"), null, new BigDecimal("10.00")));
        sales.confirm(sales.saveDraft(null, a).getId(), PaymentMethod.CARD);
        SaleForm b = new SaleForm();
        b.getLines().add(new SaleLineForm(cake, new BigDecimal("2"), null, BigDecimal.ZERO));
        Long voided = sales.saveDraft(null, b).getId();
        sales.confirm(voided, PaymentMethod.CASH);
        sales.voidSale(voided, "Entered twice");

        ExpenseForm e = new ExpenseForm();
        e.setCategoryId(expenseCategories.findByBusinessIdOrderByName(business.getId()).stream()
                .filter(c -> c.getName().equals("Rent")).findFirst().orElseThrow().getId());
        e.setDescription("Rent");
        e.setAmount(new BigDecimal("50.00"));
        e.setExpenseDate(LocalDate.now(business.zoneId()));
        expenses.save(null, e);
        today = DateRange.lastDays(business.zoneId(), 1);
    }

    @Test
    @DisplayName("TC-REP-01: revenue, COGS, gross profit, expenses and net operating result are correct; voided sales excluded")
    void profitability() {
        Profitability p = reports.profitability(business, today);
        assertThat(p.revenue()).isEqualByComparingTo("150.00");          // 160.00 - 10.00 discount, excl. tax
        assertThat(p.discounts()).isEqualByComparingTo("10.00");
        assertThat(p.taxCollected()).isEqualByComparingTo("22.50");      // 15% of 150.00
        assertThat(p.cogs()).isEqualByComparingTo("15.40");              // 4 x 3.85
        assertThat(p.grossProfit()).isEqualByComparingTo("134.60");
        assertThat(p.grossMargin()).isEqualByComparingTo("0.8973");      // 134.60 / 150.00
        assertThat(p.operatingExpenses()).isEqualByComparingTo("50.00");
        assertThat(p.netOperatingResult()).isEqualByComparingTo("84.60");
        assertThat(p.byProduct()).hasSize(1);
        assertThat(p.byProduct().get(0).units()).isEqualByComparingTo("4");
        assertThat(reports.voidedSales(business, today)).hasSize(1);
    }

    @Test
    @DisplayName("TC-REP-02: CSV export contains the report figures and is refused to an Employee")
    void csvExport() {
        String csv = reports.exportCsv(business, "profitability", today);
        assertThat(csv).contains("\"Revenue (excl. tax)\",150.00").contains("\"Net operating result\",84.60");
        loginAs(addStaffAsOwnerThenReturn(Role.EMPLOYEE));
        assertThatThrownBy(() -> reports.exportCsv(business, "profitability", today))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("TC-REP-03: inventory value equals quantity x weighted-average cost")
    void inventoryValue() {
        // flour 9000 g x 0.022 = 198.00; sugar 4500 g x 0.033 = 148.50;
        // cakes 10 - 4 - 2 + 2 (voided) = 6 x 3.85 = 23.10; total 369.60
        var value = reports.inventoryBalance(business.getId());
        BigDecimal total = value.stream().map(ReportService.StockRow::value).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("369.60");
    }

    private User addStaffAsOwnerThenReturn(Role role) {
        return addStaff(role);
    }
}
