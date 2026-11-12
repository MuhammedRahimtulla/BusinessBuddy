package com.businessbuddy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * General application beans. The {@link Clock} is injected wherever "now" matters
 * (lockouts, dates) so tests can control time.
 */
@Configuration
public class AppConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
