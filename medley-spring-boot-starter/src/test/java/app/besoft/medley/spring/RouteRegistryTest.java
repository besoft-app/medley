package app.besoft.medley.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Component;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Scope;

/**
 * Verifies the scope contract that {@code @MedleyRoute} shapes: routed components are prototype
 * (fresh instance per session), and the registry fails fast if one is singleton.
 */
class RouteRegistryTest {

    @Test
    void resolvesRouteAndYieldsFreshInstances() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(ProtoRouted.class);
            ctx.refresh();

            RouteRegistry registry = new RouteRegistry(ctx.getBeanFactory());

            assertThat(registry.resolve("/proto")).isEqualTo(ProtoRouted.class);
            // Prototype scope (carried by @MedleyRoute) => each page load gets its own instance.
            assertThat(registry.newComponent(ProtoRouted.class))
                    .isNotSameAs(registry.newComponent(ProtoRouted.class));
        }
    }

    @Test
    void failsFastWhenRoutedComponentIsSingleton() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(SingletonRouted.class);
            ctx.refresh();

            assertThatThrownBy(() -> new RouteRegistry(ctx.getBeanFactory()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("singleton");
        }
    }

    @MedleyRoute("/proto")
    @MedleyComponent("proto")
    static class ProtoRouted extends Component {
    }

    // A @Scope override on the class beats the meta-annotated prototype => singleton => must fail fast.
    @MedleyRoute("/singleton")
    @MedleyComponent("singleton")
    @Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
    static class SingletonRouted extends Component {
    }
}
