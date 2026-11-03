package com.cycling.web;

import com.fasterxml.jackson.databind.JavaType;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.PropertyCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Regeln fuer die OpenAPI-Specs, aus denen die Angular-Clients generiert werden (frontend/openapi).
 *
 * <p>Jackson schreibt jedes Feld, auch wenn es null ist. Deshalb gilt in der Spec: jedes Feld ist
 * "required" (immer vorhanden), Objekt-Typen sind "nullable", primitive Typen und Listen nicht.
 * So erzeugt der Generator {@code feld: number | null} statt {@code feld?: number}. Felder, die nie
 * null sind, werden mit {@link ApiNonNull} markiert.
 */
@Configuration
public class OpenApiConventions {

    /** Objekt-Typen (Integer, String, Dto ...) koennen null sein; primitive Typen und Listen nicht. */
    @Bean
    public PropertyCustomizer nullableObjectProperties() {
        return (property, annotatedType) -> {
            if (property == null || isPrimitive(annotatedType.getType()) || "array".equals(property.getType())
                    || hasApiNonNull(annotatedType.getCtxAnnotations())) {
                return property;
            }
            if (property.get$ref() != null) {
                // In OpenAPI 3.0 sind Geschwister von $ref verboten -> allOf-Huelle.
                ComposedSchema wrapper = new ComposedSchema();
                wrapper.addAllOfItem(new Schema<>().$ref(property.get$ref()));
                wrapper.setNullable(true);
                return wrapper;
            }
            property.setNullable(true);
            return property;
        };
    }

    /** Alle (nicht nur beschreibbaren) Felder sind in der Antwort immer vorhanden. */
    @Bean
    public OpenApiCustomizer allPropertiesRequired() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) return;
            for (Schema<?> schema : openApi.getComponents().getSchemas().values()) {
                Map<String, Schema> properties = schema.getProperties();
                if (properties == null) continue;
                properties.values().forEach(OpenApiConventions::nonNullableItems);
                List<String> required = new ArrayList<>();
                properties.forEach((name, property) -> {
                    if (!Boolean.TRUE.equals(property.getWriteOnly())) required.add(name);
                });
                schema.setRequired(required);
            }
        };
    }

    /** Listen enthalten keine null-Eintraege: nullable-Huelle der Elemente wieder entfernen. */
    private static void nonNullableItems(Schema<?> property) {
        Schema<?> items = property.getItems();
        if (items == null) return;
        if (items instanceof ComposedSchema composed && composed.getAllOf() != null && composed.getAllOf().size() == 1) {
            property.setItems(new Schema<>().$ref(composed.getAllOf().getFirst().get$ref()));
        } else {
            items.setNullable(null);
        }
    }

    private static boolean hasApiNonNull(java.lang.annotation.Annotation[] annotations) {
        if (annotations == null) return false;
        for (java.lang.annotation.Annotation a : annotations) {
            if (a.annotationType() == ApiNonNull.class) return true;
        }
        return false;
    }

    private static boolean isPrimitive(Type type) {
        if (type instanceof Class<?> c) return c.isPrimitive();
        if (type instanceof JavaType jt) return jt.isPrimitive();
        return false;
    }
}
