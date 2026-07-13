package app.besoft.medley.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.besoft.medley.spring.fixtures.TestCounterComponent;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Stage 5, increment 1 — metrics. The Micrometer-backed {@link MicrometerMedleyMetrics} registers the
 * expected meters, the {@link MedleyMetrics#NOOP} default is inert, and the WebSocket handler records
 * one message per action with its patch count — so the framework's minimal-diff property is observable
 * in production.
 */
class MedleyMetricsTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private static final String INCREMENT = "{\"componentId\":\"root\",\"action\":\"increment\"}";

    @Test
    void micrometerImplPublishesCounterTimerAndPatchSummary() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MedleyMetrics metrics = new MicrometerMedleyMetrics(registry);

        metrics.recordMessage("action", 3, 1_000_000L, true);

        assertThat(registry.get("medley.messages")
                .tags("type", "action", "outcome", "success").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("medley.message.duration").tags("type", "action").timer().count()).isEqualTo(1L);
        var summary = registry.get("medley.patches").summary();
        assertThat(summary.count()).isEqualTo(1L);
        assertThat(summary.totalAmount()).isEqualTo(3.0);
    }

    @Test
    void failedMessageIsCountedAsErrorAndExcludedFromPatchSummary() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MedleyMetrics metrics = new MicrometerMedleyMetrics(registry);

        metrics.recordMessage("action", 0, 500L, false);

        assertThat(registry.get("medley.messages")
                .tags("type", "action", "outcome", "error").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("medley.patches").summary().count())
                .as("a failed message records no patch sample").isZero();
    }

    @Test
    void noopMetricsAreInert() {
        MedleyMetrics.NOOP.recordMessage("action", 5, 1L, true); // must not throw or register anything
    }

    @Test
    void handlerRecordsEachActionWithItsPatchCount() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Fixture f = handlerWith(registry);

        f.handler().handleTextMessage(f.ws(), new TextMessage(INCREMENT)); // 0 -> 1
        f.handler().handleTextMessage(f.ws(), new TextMessage(INCREMENT)); // 1 -> 2 (steady: one patch)

        assertThat(registry.get("medley.messages")
                .tags("type", "action", "outcome", "success").counter().count())
                .as("both successful actions counted").isEqualTo(2.0);
        assertThat(registry.get("medley.patches").summary().count())
                .as("one patch sample recorded per action").isEqualTo(2L);
    }

    @Test
    void handlerRecordsAFailedActionAsError() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Fixture f = handlerWith(registry);

        f.handler().handleTextMessage(f.ws(), new TextMessage("{\"componentId\":\"root\",\"action\":\"bogus\"}"));

        assertThat(registry.get("medley.messages")
                .tags("type", "action", "outcome", "error").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("medley.patches").summary().count())
                .as("a failed action records no patch sample").isZero();
    }

    @Test
    void handlerRecordsResync() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Fixture f = handlerWith(registry);

        f.handler().handleTextMessage(f.ws(), new TextMessage("{\"type\":\"resync\"}"));

        assertThat(registry.get("medley.messages")
                .tags("type", "resync", "outcome", "success").counter().count()).isEqualTo(1.0);
    }

    // --- helpers -----------------------------------------------------------------------------------

    /** A handler wired with the given registry, plus a mock socket carrying a session with a mounted root. */
    private record Fixture(MedleyWebSocketHandler handler, WebSocketSession ws) {}

    private Fixture handlerWith(SimpleMeterRegistry registry) {
        MedleyWebSocketHandler handler = new MedleyWebSocketHandler(
                mapper, new PatchEncoder(mapper),
                new IslandRegistry(new DefaultListableBeanFactory(), mapper),
                0, new MicrometerMedleyMetrics(registry));
        MedleySession session = new MedleySession(new TemplateRegistry("templates/medley/"));
        session.mount("root", new TestCounterComponent()).renderInitialHtml();
        WebSocketSession ws = mock(WebSocketSession.class);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(MedleySession.class.getName(), session);
        when(ws.getAttributes()).thenReturn(attrs);
        return new Fixture(handler, ws);
    }
}
