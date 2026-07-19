package app.besoft.medley.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Stage 5, increment 1 — the metrics bean is <em>optional</em>. {@link MicrometerMedleyMetrics} is wired
 * only when Micrometer + a {@code MeterRegistry} bean are present; otherwise no {@link MedleyMetrics}
 * bean exists and the handler falls back to {@link MedleyMetrics#NOOP}. Exercises
 * {@link MedleyMetricsAutoConfiguration} in isolation.
 */
class MedleyMetricsAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MedleyMetricsAutoConfiguration.class));

    @Configuration(proxyBeanMethods = false)
    static class WithRegistry {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Test
    void metricsWiredWhenAMeterRegistryIsPresent() {
        runner.withUserConfiguration(WithRegistry.class).run(ctx ->
                assertThat(ctx).getBean(MedleyMetrics.class).isInstanceOf(MicrometerMedleyMetrics.class));
    }

    @Test
    void noMetricsBeanWithoutAMeterRegistry() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(MedleyMetrics.class));
    }

    @Test
    void metricsWiredAfterActuatorCreatesTheRegistry() {
        // Mirrors production: Actuator's export auto-config creates the MeterRegistry, and Medley's
        // metrics auto-config (ordered afterName) must see it. Guards the auto-config ordering bug.
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        MetricsAutoConfiguration.class,
                        CompositeMeterRegistryAutoConfiguration.class,
                        SimpleMetricsExportAutoConfiguration.class,
                        MedleyMetricsAutoConfiguration.class))
                .run(ctx -> assertThat(ctx).getBean(MedleyMetrics.class)
                        .isInstanceOf(MicrometerMedleyMetrics.class));
    }

    @Test
    void configSkippedWhenMicrometerIsAbsentFromTheClasspath() {
        runner.withClassLoader(new FilteredClassLoader(MeterRegistry.class)).run(ctx -> {
            assertThat(ctx).hasNotFailed(); // @ConditionalOnClass skips the config without loading Micrometer
            assertThat(ctx).doesNotHaveBean(MedleyMetrics.class);
        });
    }
}
