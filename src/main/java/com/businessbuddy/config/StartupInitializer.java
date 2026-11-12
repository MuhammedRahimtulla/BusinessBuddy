package com.businessbuddy.config;

import com.businessbuddy.entity.Business;
import com.businessbuddy.service.DemoDataService;
import com.businessbuddy.service.ForecastService;
import com.businessbuddy.service.NightlyJobs;
import com.businessbuddy.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Runs once at start-up:
 * <ol>
 *   <li>creates the first Administrator from BUSINESSBUDDY_ADMIN_EMAIL and BUSINESSBUDDY_ADMIN_PASSWORD
 *       if no Administrator exists (credentials are never in source code);</li>
 *   <li>when demo data is enabled and absent, generates the labelled sample business and runs the
 *       nightly stock and forecast jobs for it.</li>
 * </ol>
 */
@Component
public class StartupInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupInitializer.class);

    private final UserService users;
    private final DemoDataService demo;
    private final NightlyJobs jobs;
    private final ForecastService forecasts;

    @Value("${businessbuddy.admin.email:}")
    private String adminEmail;

    @Value("${businessbuddy.admin.password:}")
    private String adminPassword;

    @Value("${businessbuddy.demo-data:false}")
    private boolean demoData;

    @Value("${businessbuddy.demo.password:}")
    private String demoPassword;

    @Value("${businessbuddy.demo.days:120}")
    private int demoDays;

    public StartupInitializer(UserService users, DemoDataService demo, NightlyJobs jobs, ForecastService forecasts) {
        this.users = users;
        this.demo = demo;
        this.jobs = jobs;
        this.forecasts = forecasts;
    }

    @Override
    public void run(ApplicationArguments args) {
        users.ensureAdministrator(adminEmail == null ? null : adminEmail.trim().toLowerCase(), adminPassword);
        if (demoData && !demo.exists()) {
            if (demoPassword == null || demoPassword.isBlank()) {
                log.warn("Demo data is enabled but businessbuddy.demo.password is not set; skipping sample data");
                return;
            }
            long started = System.currentTimeMillis();
            log.info("Generating the sample business (this happens once)...");
            Business business = demo.seed(demoPassword, demoDays);
            jobs.stockJob(business);
            forecasts.runAll(business);
            log.info("Sample business ready in {} s", (System.currentTimeMillis() - started) / 1000);
        }
    }
}
