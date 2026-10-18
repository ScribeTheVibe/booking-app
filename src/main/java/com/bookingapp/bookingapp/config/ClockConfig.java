package com.bookingapp.bookingapp.config;

import com.bookingapp.bookingapp.entity.ResortTime;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** One shared clock in the resort's time zone, so tests can replace it with a fixed one. */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ResortTime.ZONE);
    }
}