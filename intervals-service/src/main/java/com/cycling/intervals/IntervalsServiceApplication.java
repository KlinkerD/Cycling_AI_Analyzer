package com.cycling.intervals;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IntervalsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntervalsServiceApplication.class, args);
    }
}
