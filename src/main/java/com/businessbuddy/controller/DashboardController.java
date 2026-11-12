package com.businessbuddy.controller;

import com.businessbuddy.entity.Business;
import com.businessbuddy.security.BusinessContext;
import com.businessbuddy.service.reporting.DashboardService;
import com.businessbuddy.util.DateRange;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

/**
 * The dashboard (Figure 4.13). Owners and Managers see money figures; Employees see the
 * operational view without costs or profits (Table 4.37).
 */
@Controller
public class DashboardController {

    private final DashboardService dashboard;
    private final BusinessContext context;

    public DashboardController(DashboardService dashboard, BusinessContext context) {
        this.dashboard = dashboard;
        this.context = context;
    }

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            Model model) {
        Business business = context.currentBusiness();
        DateRange period = DateRange.of(from, to, business.zoneId());
        model.addAttribute("period", period);
        model.addAttribute("d", dashboard.build(business, period, context.currentUser().canSeeMoney()));
        return "dashboard";
    }
}
