package com.cycling.web;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Fuer den OpenApiSpecTest jedes Web-Service: holt die Spec vom laufenden Service und vergleicht sie mit der
 * eingecheckten Datei unter frontend/openapi. Mit {@code -Dopenapi.update=true} (oder wenn die Datei fehlt)
 * wird die Datei stattdessen geschrieben.
 */
public final class OpenApiSpecFile {

    private OpenApiSpecFile() {
    }

    /**
     * @param port          lokaler Port des Services im Test
     * @param spec          Pfad der Spec-Datei, relativ zum Modulverzeichnis
     * @param updateCommand Befehl zum Aktualisieren (fuer die Fehlermeldung)
     * @throws AssertionError wenn die Spec von der Datei abweicht
     */
    public static void verify(int port, Path spec, String updateCommand) throws IOException, InterruptedException {
        URI uri = URI.create("http://localhost:" + port + "/v3/api-docs.yaml");
        HttpResponse<String> response;
        try (HttpClient http = HttpClient.newHttpClient()) {
            response = http.send(HttpRequest.newBuilder(uri).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
        if (response.statusCode() != 200) {
            throw new AssertionError("Spec nicht abrufbar: HTTP " + response.statusCode());
        }
        String actual = response.body().replace("\r\n", "\n");

        if (Boolean.getBoolean("openapi.update") || !Files.exists(spec)) {
            try {
                Files.createDirectories(spec.getParent());
                Files.writeString(spec, actual, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return;
        }
        String committed = Files.readString(spec, StandardCharsets.UTF_8).replace("\r\n", "\n");
        if (!actual.equals(committed)) {
            throw new AssertionError("OpenAPI-Spec " + spec + " veraltet. Aktualisieren mit: " + updateCommand);
        }
    }
}
