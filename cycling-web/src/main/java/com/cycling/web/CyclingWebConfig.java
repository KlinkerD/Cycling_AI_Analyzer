package com.cycling.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.List;

/**
 * Per {@code @Import} in jeden Service mit Frontend-API einbinden: OpenAPI-Regeln und ProblemDetail
 * fuer Fehler des intervals-service.
 */
@Configuration
@Import({OpenApiConventions.class, IntervalsProblemHandler.class})
public class CyclingWebConfig {

    /**
     * Spec-Kopf fuer einen Service. Server relativ ("/"), damit die Spec nicht vom zufaelligen Port
     * abhaengt; das Frontend ruft ueber nginx bzw. den Dev-Proxy auf.
     */
    public static OpenAPI openApi(String title) {
        return new OpenAPI()
                .info(new Info().title(title).version("1"))
                .servers(List.of(new Server().url("/")));
    }
}
