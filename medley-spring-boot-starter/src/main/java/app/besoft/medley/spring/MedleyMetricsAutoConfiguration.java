package app.besoft.medley.spring;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Optional observability (Stage 5, increment 1). Registers a Micrometer-backed {@link MedleyMetrics}
 * only when Micrometer is on the classpath <em>and</em> a {@code MeterRegistry} bean is present (e.g.
 * the app added {@code spring-boot-starter-actuator}). Kept as a separate auto-configuration — not a
 * bean in {@link MedleyAutoConfiguration} — so the core auto-config never references Micrometer, and
 * {@code @ConditionalOnBean} is evaluated in proper auto-configuration order.
 *
 * <p>It runs <em>after</em> Actuator's metrics auto-configuration (via {@code afterName}, so no compile
 * dependency on Actuator): the {@code MeterRegistry} is created there, and {@code @ConditionalOnBean}
 * would otherwise be evaluated before it exists and silently skip. When Micrometer/registry are absent,
 * {@link MedleyAutoConfiguration} wires the handler with {@link MedleyMetrics#NOOP}.</p>
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration",
        "org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration"
})
@ConditionalOnClass(MeterRegistry.class)
public class MedleyMetricsAutoConfiguration {

    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean(MedleyMetrics.class)
    public MedleyMetrics medleyMetrics(MeterRegistry registry) {
        return new MicrometerMedleyMetrics(registry);
    }
}
