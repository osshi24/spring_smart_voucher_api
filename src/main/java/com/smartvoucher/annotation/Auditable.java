package com.smartvoucher.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {
    String action();
    String entityType();
    /**
     * SpEL expression to extract the entity ID (Long) from method params or return value.
     * Examples: "#id", "#voucherId", "#result?.id"
     * Leave empty to log with null entityId.
     */
    String entityIdSpel() default "";
}
