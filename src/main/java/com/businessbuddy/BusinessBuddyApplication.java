package com.businessbuddy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point of BusinessBuddy: AI-Powered Business Cost and Inventory Management System.
 *
 * <p>Starts the embedded web server, runs the Flyway migrations against businessbuddy_db
 * and enables the nightly scheduled jobs (stock checks, forecasts).</p>
 */
@SpringBootApplication
@EnableScheduling
public class BusinessBuddyApplication {

    public static void main(String[] args) {
        SpringApplication.run(BusinessBuddyApplication.class, args);
    }
}
