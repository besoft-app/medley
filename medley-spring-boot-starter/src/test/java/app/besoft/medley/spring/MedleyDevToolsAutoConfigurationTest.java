package app.besoft.medley.spring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Stage 5, increment 2 — the dev-tools are <em>opt-in</em>. The inspector exposes a session's
 * {@code @State}, so nothing may exist unless {@code medley.devtools.enabled=true} is set explicitly.
 * Exercises {@link MedleyDevToolsAutoConfiguration} in isolation.
 */
class MedleyDevToolsAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class, MedleyDevToolsAutoConfiguration.class));

    @Test
    void devToolsAreAbsentByDefault() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).doesNotHaveBean(MedleyDevToolsController.class);
        });
    }

    @Test
    void devToolsAreAbsentWhenExplicitlyDisabled() {
        runner.withPropertyValues("medley.devtools.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(MedleyDevToolsController.class));
    }

    @Test
    void devToolsAreWiredWhenEnabled() {
        runner.withPropertyValues("medley.devtools.enabled=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(MedleyDevToolsController.class));
    }
}
