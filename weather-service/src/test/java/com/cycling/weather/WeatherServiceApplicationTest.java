package com.cycling.weather;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Der Kontext startet mit den Default-Properties (ohne Aufruf bei Open-Meteo). */
@SpringBootTest
class WeatherServiceApplicationTest {

    @Autowired
    private WeatherController controller;

    @Test
    void contextLoads() {
        assertThat(controller).isNotNull();
    }
}
