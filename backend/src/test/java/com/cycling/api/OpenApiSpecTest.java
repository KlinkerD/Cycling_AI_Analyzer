package com.cycling.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exportiert die OpenAPI-Spec des Backends nach frontend/openapi/cycling-api.yaml. Daraus erzeugt
 * der OpenAPI Generator die Angular-Models und -Services.
 *
 * <p>Weicht die API von der eingecheckten Datei ab, schlaegt der Test fehl. Aktualisieren mit:
 * {@code mvn -pl backend test -Dtest=OpenApiSpecTest -Dopenapi.update=true}
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:openapi;DB_CLOSE_DELAY=-1",
        "mqtt.broker-url=tcp://localhost:1",
        "mqtt.client-id=openapi-test"
})
class OpenApiSpecTest {

    static final Path SPEC = Path.of("..", "frontend", "openapi", "cycling-api.yaml");

    @Autowired
    private Environment environment;

    @Test
    void specFileMatchesApi() throws Exception {
        URI uri = URI.create("http://localhost:" + environment.getProperty("local.server.port") + "/v3/api-docs.yaml");
        HttpResponse<String> response;
        try (HttpClient http = HttpClient.newHttpClient()) {
            response = http.send(HttpRequest.newBuilder(uri).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
        assertThat(response.statusCode()).isEqualTo(200);
        String actual = response.body().replace("\r\n", "\n");

        boolean update = Boolean.getBoolean("openapi.update");
        if (update || !Files.exists(SPEC)) {
            Files.createDirectories(SPEC.getParent());
            Files.writeString(SPEC, actual, StandardCharsets.UTF_8);
            return;
        }
        String committed = Files.readString(SPEC, StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertThat(actual)
                .as("OpenAPI-Spec veraltet. Aktualisieren mit: mvn -pl backend test -Dtest=OpenApiSpecTest -Dopenapi.update=true")
                .isEqualTo(committed);
    }
}
