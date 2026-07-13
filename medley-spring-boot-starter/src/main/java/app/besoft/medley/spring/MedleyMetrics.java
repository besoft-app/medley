package app.besoft.medley.spring;

/**
 * Optional observability hook for the Medley render loop (Stage 5, increment 1). The WebSocket handler
 * calls {@link #recordMessage} once per processed inbound message; the default {@link #NOOP} does
 * nothing, so there is zero overhead and no dependency unless observability is wired.
 *
 * <p>This interface is deliberately Micrometer-free — the handler measures elapsed nanos and passes
 * plain values, so it never imports Micrometer. The {@code MicrometerMedleyMetrics} implementation is
 * auto-configured only when a {@code MeterRegistry} is on the classpath and present as a bean (e.g. the
 * app added {@code spring-boot-starter-actuator}). medley-core stays free of both Spring and Micrometer.</p>
 */
@FunctionalInterface
public interface MedleyMetrics {

    /**
     * Record one processed WebSocket message.
     *
     * @param type         message type: {@code "action"}, {@code "island-commit"}, or {@code "resync"}
     * @param patchCount   patches emitted in the response (0 on error or a {@code reload})
     * @param elapsedNanos wall-clock nanoseconds spent handling it
     * @param ok           whether it completed without a handler failure
     */
    void recordMessage(String type, int patchCount, long elapsedNanos, boolean ok);

    /** No-op metrics — the default when no {@code MeterRegistry} is available. */
    MedleyMetrics NOOP = (type, patchCount, elapsedNanos, ok) -> { };
}
