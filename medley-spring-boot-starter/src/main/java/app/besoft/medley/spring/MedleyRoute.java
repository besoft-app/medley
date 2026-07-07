package app.besoft.medley.spring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.stereotype.Component;

/**
 * Maps a URL path to a root Medley component. The annotated class must also be a
 * {@code @app.besoft.medley.core.component.Annotations.MedleyComponent} and extend
 * {@code app.besoft.medley.core.component.Component}.
 *
 * <p>{@code @Component} is meta-present so the class is picked up by component scanning as a
 * Spring bean (prototype scope is applied by the registrar).</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Component
public @interface MedleyRoute {
    String value();
}
