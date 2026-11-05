package com.cycling.stats.api;

import com.cycling.web.OpenApiSpecFile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;

/**
 * Exportiert die OpenAPI-Spec des stats-service nach frontend/openapi/stats-api.yaml (Angular-Client
 * unter src/app/api/stats). Weicht die API ab, schlaegt der Test fehl. Aktualisieren mit:
 * {@code mvn -pl stats-service test -Dtest=OpenApiSpecTest -Dopenapi.update=true}
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "mqtt.broker-url=tcp://localhost:1",
        "mqtt.client-id=stats-openapi-test",
        "intervals-service.base-url=http://localhost:1",
        "stats.import.on-start=false",
        "spring.datasource.url=jdbc:h2:mem:stats-openapi;DB_CLOSE_DELAY=-1"
})
class OpenApiSpecTest {

    @Value("${local.server.port}")
    private int port;

    @Test
    void specFileMatchesApi() throws Exception {
        OpenApiSpecFile.verify(port, Path.of("..", "frontend", "openapi", "stats-api.yaml"),
                "mvn -pl stats-service test -Dtest=OpenApiSpecTest -Dopenapi.update=true");
    }
}
