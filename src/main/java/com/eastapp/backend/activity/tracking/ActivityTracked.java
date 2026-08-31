package com.eastapp.backend.activity.tracking;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ActivityTracked {
    String module();
    String action();
    String entity();
    String targetPathVariable() default "";
}
