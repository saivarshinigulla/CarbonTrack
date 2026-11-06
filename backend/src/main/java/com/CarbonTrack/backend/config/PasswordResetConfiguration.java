package com.CarbonTrack.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class PasswordResetConfiguration {

    @Bean
    public Clock passwordResetClock() {
        return Clock.systemUTC();
    }
}
