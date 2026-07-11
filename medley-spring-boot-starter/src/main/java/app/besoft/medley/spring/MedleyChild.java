package app.besoft.medley.spring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Marks a {@code @MedleyComponent} class as a reusable <em>child</em> component — one that can be
 * nested in another component's template via {@code <medley-component name="...">} (Stage 4,
 * increment 4b). The annotated class must also be a
 * {@code @app.besoft.medley.core.component.Annotations.MedleyComponent} and extend
 * {@code app.besoft.medley.core.component.Component}.
 *
 * <p>Mirrors {@link MedleyRoute}: {@code @Component} is meta-present so the class is a scannable
 * Spring bean, and {@code @Scope(prototype)} is meta-present so <em>each placement</em> gets its own
 * instance with its own {@code @State} and full dependency injection — child state is never shared.
 * {@link ComponentRegistry} fails fast at startup if a child component is nonetheless singleton.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public @interface MedleyChild {
}
