package com.businessbuddy;

import com.businessbuddy.entity.Business;
import com.businessbuddy.entity.Enums.ForecastStatus;
import com.businessbuddy.entity.Enums.ProductType;
import com.businessbuddy.entity.Forecast;
import com.businessbuddy.entity.ModelEvaluation;
import com.businessbuddy.entity.Product;
import com.businessbuddy.entity.User;
import com.businessbuddy.repository.ProductRepository;
import com.businessbuddy.security.CurrentUser;
import com.businessbuddy.service.DemoDataService;
import com.businessbuddy.service.ForecastService;
import com.businessbuddy.service.NightlyJobs;
import com.businessbuddy.service.inventory.InventoryService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * System tests: the complete application with a generated sample business (75 days of
 * labelled demonstration data created through the real services). Every screen is opened
 * through the full web stack for each role, and the forecasting pipeline is run end to end
 * (FR-15 to FR-18, spec 16 "End-to-end or system tests" and "Machine learning tests").
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SystemIT extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired DemoDataService demo;
    @Autowired NightlyJobs jobs;
    @Autowired ForecastService forecasts;
    @Autowired InventoryService inventory;
    @Autowired ProductRepository products;

    Business business;
    CurrentUser owner;
    CurrentUser manager;
    CurrentUser employee;

    @BeforeAll
    void seed() {
        business = businesses.findFirstByDemoDataTrue().orElseGet(() -> demo.seed(PASSWORD, 75));
        jobs.stockJob(business);
        forecasts.runAll(business);
        owner = principal(DemoDataService.OWNER_EMAIL);
        manager = principal(DemoDataService.MANAGER_EMAIL);
        employee = principal(DemoDataService.EMPLOYEE_EMAIL);
    }

    private CurrentUser principal(String email) {
        User u = users.findByEmail(email).orElseThrow();
        return new CurrentUser(u, Instant.now());
    }

    @Test
    @DisplayName("TC-SYS-01: every Owner screen opens without an error and shows the sample-data banner")
    void ownerScreens() throws Exception {
        loginAs(users.findByEmail(DemoDataService.OWNER_EMAIL).orElseThrow());
        Product cake = products.findByBusinessIdOrderByName(business.getId()).stream()
                .filter(p -> p.getType() == ProductType.MANUFACTURED).findFirst().orElseThrow();
        Long recipeId = recipes.activeRecipe(cake.getId()).orElseThrow().getId();
        List<String> pages = List.of("/dashboard", "/ingredients", "/ingredients/new", "/inventory",
                "/inventory/movements", "/inventory/waste", "/inventory/adjust", "/suppliers", "/suppliers/new",
                "/purchases", "/purchases/new", "/products", "/products/new", "/products/" + cake.getId(),
                "/recipes/" + recipeId, "/recipes/new?productId=" + cake.getId(),
                "/pricing?productId=" + cake.getId(), "/production", "/production/new", "/sales", "/sales/new",
                "/customers", "/expenses", "/expenses/new", "/reports", "/reports/profitability", "/reports/inventory",
                "/reports/reconciliation", "/reports/sales", "/reports/expenses", "/reports/purchases",
                "/reports/costing", "/forecasts", "/forecasts/product/" + cake.getId(), "/insights",
                "/notifications", "/settings/business", "/settings/categories", "/settings/users", "/profile", "/audit");
        for (String page : pages) {
            mvc.perform(get(page).with(user(owner)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("Sample data")));
        }
    }

    @Test
    @DisplayName("TC-SYS-02: an Employee sees the operational dashboard without money figures")
    void employeeDashboard() throws Exception {
        mvc.perform(get("/dashboard").with(user(employee)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Gross profit"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Items below minimum")));
        mvc.perform(get("/sales").with(user(employee))).andExpect(status().isOk());
        mvc.perform(get("/production").with(user(employee))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-SYS-03: a Manager can open reports and export CSV")
    void managerReports() throws Exception {
        mvc.perform(get("/reports/export/profitability").with(user(manager)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Net operating result")));
    }

    @Test
    @DisplayName("TC-SYS-04: a sale entered through the web form is confirmed and appears in the history")
    void saleThroughWebForm() throws Exception {
        Product product = products.findByBusinessIdOrderByName(business.getId()).stream()
                .filter(p -> p.getCurrentQuantity().signum() > 0).findFirst().orElseThrow();
        mvc.perform(post("/sales").with(user(employee)).with(csrf())
                        .param("action", "confirm").param("paymentMethod", "CASH")
                        .param("lines[0].productId", product.getId().toString())
                        .param("lines[0].quantity", "1").param("lines[0].discountAmount", "0"))
                .andExpect(status().is3xxRedirection());
        assertThat(products.findById(product.getId()).orElseThrow().getCurrentQuantity())
                .isEqualByComparingTo(product.getCurrentQuantity().subtract(java.math.BigDecimal.ONE));
    }

    @Test
    @DisplayName("TC-ML-07: with 75 days of history each product gets a model evaluation and 7 non-negative forecasts")
    void forecastsStored() {
        // the five sample products have 75 days of history; products added by other tests have none
        List<Forecast> current = forecasts.currentDemand(business.getId()).stream()
                .filter(f -> f.getStatus() != ForecastStatus.INSUFFICIENT_DATA).toList();
        assertThat(current).hasSize(5 * 7);
        assertThat(current).allSatisfy(f -> {
            assertThat(f.getStatus()).isEqualTo(ForecastStatus.ACTIVE);
            assertThat(f.getPredictedValue()).isNotNegative();
            assertThat(f.getTargetDate()).isAfter(f.getTrainingCutoff());     // forecasts are about the future
            assertThat(f.getActualValue()).isNull();                          // not mixed with actuals
        });
        List<ModelEvaluation> evaluations = forecasts.evaluations(business.getId());
        assertThat(evaluations).isNotEmpty();
        assertThat(evaluations).allSatisfy(e -> {
            assertThat(e.getTrainEnd()).isBefore(e.getTestStart());           // chronological split
            assertThat(e.getSelectedModel()).isIn("REGRESSION", "MOVING_AVERAGE_28");
        });
    }

    @Test
    @DisplayName("TC-ML-08: a product with no sales history gets INSUFFICIENT_DATA instead of a forecast")
    void insufficientData() {
        User o = users.findByEmail(DemoDataService.OWNER_EMAIL).orElseThrow();
        loginAs(o);
        Long newProduct = product("Brand-new product " + System.nanoTime(), ProductType.RESALE, "10.00");
        ForecastService.DemandResult r = forecasts.runDemandForecast(business, products.findById(newProduct).orElseThrow());
        assertThat(r.sufficientData()).isFalse();
        assertThat(r.forecasts()).singleElement().satisfies(f -> {
            assertThat(f.getStatus()).isEqualTo(ForecastStatus.INSUFFICIENT_DATA);
            assertThat(f.getPredictedValue()).isNull();
        });
    }

    @Test
    @DisplayName("TC-SYS-05: the generated sample data reconciles: every item's stock equals its ledger total")
    void sampleDataReconciles() {
        assertThat(inventory.reconcile(business.getId())).allSatisfy(r -> assertThat(r.matches()).isTrue());
        assertThat(jobs.stockJob(business)).isZero();
        assertThat(LocalDate.now()).isNotNull();
    }
}
