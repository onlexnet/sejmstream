package onlexnet.shared;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AliasFor;

/**
 * Composed annotation for Spring module configuration classes.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ComponentScan
@ConfigurationPropertiesScan
@Configuration
public @interface ModuleConfiguration {

    /**
     * Alias for {@link Configuration#value()}.
     *
     * @return optional suggested component name for the annotated configuration class
     */
    @AliasFor(annotation = Configuration.class)
    String value() default "";
}