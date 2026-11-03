package com.cycling.training.api;

import com.cycling.web.OpenApiSpecFile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;

/**
 * Exportiert die OpenAPI-Spec des training-service nach frontend/openapi/training-api.yaml (Angular-Client
 * unter src/app/api/training). Weicht die API ab, schlaegt der Test fehl. Aktualisieren mit:
 * {@code mvn -pl training-service test -Dtest=OpenApiSpecTest -Dopenapi.update=true}
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "mqtt.broker-url=tcp://localhost:1",
        "mqtt.client-id=training-openapi-test",
        "intervals-service.base-url=http://localhost:1"
})
class OpenApiSpecTest {

    @Value("${local.server.port}")
    private int port;

    @Test
    void specFileMatchesApi() throws Exception {
        OpenApiSpecFile.verify(port, Path.of("..", "frontend", "openapi", "training-api.yaml"),
                "mvn -pl training-service test -Dtest=OpenApiSpecTest -Dopenapi.update=true");
    }
}
