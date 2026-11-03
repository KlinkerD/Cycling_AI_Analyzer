package com.cycling.fitness.api;

import com.cycling.web.OpenApiSpecFile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;

/**
 * Exportiert die OpenAPI-Spec des fitness-service nach frontend/openapi/fitness-api.yaml (Angular-Client
 * unter src/app/api/fitness). Weicht die API ab, schlaegt der Test fehl. Aktualisieren mit:
 * {@code mvn -pl fitness-service test -Dtest=OpenApiSpecTest -Dopenapi.update=true}
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "mqtt.broker-url=tcp://localhost:1",
        "mqtt.client-id=fitness-openapi-test"
})
class OpenApiSpecTest {

    @Value("${local.server.port}")
    private int port;

    @Test
    void specFileMatchesApi() throws Exception {
        OpenApiSpecFile.verify(port, Path.of("..", "frontend", "openapi", "fitness-api.yaml"),
                "mvn -pl fitness-service test -Dtest=OpenApiSpecTest -Dopenapi.update=true");
    }
}
