package app.besoft.medley.spring;

import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Micrometer-backed {@link MedleyMetrics} (Stage 5, increment 1). Registered by the auto-configuration
 * only when Micrometer + a {@code MeterRegistry} are available. It publishes:
 *
 * <ul>
 *   <li>{@code medley.messages} — counter, tags {@code type} (action/island-commit/resync) and
 *       {@code outcome} (success/error): inbound WebSocket message volume;</li>
 *   <li>{@code medley.message.duration} — timer, tag {@code type}: server handling time per message;</li>
 *   <li>{@code medley.patches} — distribution summary: patches emitted per successful update, so the
 *       framework's minimal-diff guarantee is observable in production.</li>
 * </ul>
 *
 * <p>This is the only class that imports Micrometer; it loads solely when the metrics bean is created.</p>
 */
public final class MicrometerMedleyMetrics implements MedleyMetrics {

    private final MeterRegistry registry;
    private final DistributionSummary patches;

    public MicrometerMedleyMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.patches = DistributionSummary.builder("medley.patches")
                .description("Patches emitted per successful Medley update")
                .register(registry);
    }

    @Override
    public void recordMessage(String type, int patchCount, long elapsedNanos, boolean ok) {
        registry.counter("medley.messages", "type", type, "outcome", ok ? "success" : "error").increment();
        registry.timer("medley.message.duration", "type", type).record(elapsedNanos, TimeUnit.NANOSECONDS);
        if (ok) {
            patches.record(patchCount);
        }
    }
}
