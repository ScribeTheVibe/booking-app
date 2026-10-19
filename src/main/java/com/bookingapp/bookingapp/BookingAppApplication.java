package com.bookingapp.bookingapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class BookingAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingAppApplication.class, args);
    }

}
