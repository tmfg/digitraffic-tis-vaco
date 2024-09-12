package fi.digitraffic.tis.vaco;

import org.immutables.value.Value;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Common Immutables styling for all domain values. Apply in place of simply defining <code>@Value.Immutable</code>
 */
@Target({ElementType.PACKAGE, ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
@Value.Style(
    jdkOnly = true, // block Guava from being picked from classpath to avoid issues with surprising collection implementations
    jdk9Collections = true,  // use collection constructors
    defaultAsDefault = true // default method is used as default values without need for special annotating
    )
public @interface DomainValue {}
