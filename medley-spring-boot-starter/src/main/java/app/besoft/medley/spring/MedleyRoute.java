package app.besoft.medley.spring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Maps a URL path to a root Medley component. The annotated class must also be a
 * {@code @app.besoft.medley.core.component.Annotations.MedleyComponent} and extend
 * {@code app.besoft.medley.core.component.Component}.
 *
 * <p>{@code @Component} is meta-present so the class is picked up by component scanning as a
 * Spring bean. {@code @Scope(prototype)} is meta-present (the same mechanism Spring's own
 * {@code @SessionScope}/{@code @RequestScope} use) so every page load gets a <em>fresh</em>
 * instance with its own {@code @State} and full dependency injection — server-side state is
 * thereby bounded to the session, not shared across users. {@link RouteRegistry} fails fast at
 * startup if a routed component is nonetheless singleton (e.g. a {@code @Scope} override).</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public @interface MedleyRoute {
    String value();
}
