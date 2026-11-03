package com.cycling.report.api;

import com.cycling.web.OpenApiSpecFile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;

/**
 * Exportiert die OpenAPI-Spec des report-service nach frontend/openapi/report-api.yaml. Daraus erzeugt
 * der OpenAPI Generator den Angular-Client (src/app/api/report).
 *
 * <p>Weicht die API von der eingecheckten Datei ab, schlaegt der Test fehl. Aktualisieren mit:
 * {@code mvn -pl report-service test -Dtest=OpenApiSpecTest -Dopenapi.update=true}
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:openapi;DB_CLOSE_DELAY=-1",
        "mqtt.broker-url=tcp://localhost:1",
        "mqtt.client-id=openapi-test"
})
class OpenApiSpecTest {

    @Value("${local.server.port}")
    private int port;

    @Test
    void specFileMatchesApi() throws Exception {
        OpenApiSpecFile.verify(port, Path.of("..", "frontend", "openapi", "report-api.yaml"),
                "mvn -pl report-service test -Dtest=OpenApiSpecTest -Dopenapi.update=true");
    }
}
