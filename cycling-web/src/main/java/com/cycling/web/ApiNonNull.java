package com.cycling.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Markiert ein Feld der REST-API, das nie null ist. Ohne diese Annotation gilt jedes Objekt-Feld
 * in der OpenAPI-Spec als nullable (siehe {@link OpenApiConventions}), im Angular-Client also {@code T | null}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
public @interface ApiNonNull {
}
