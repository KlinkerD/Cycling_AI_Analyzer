package com.cycling.delivery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ReportDeliveryApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReportDeliveryApplication.class, args);
    }
}
