package com.cycling.fitness;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FitnessServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(FitnessServiceApplication.class, args);
    }
}
