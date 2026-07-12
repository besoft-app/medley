package app.besoft.medley.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import app.besoft.medley.spring.fixtures.EvictableChildComponent;

import java.util.Map;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionBindingEvent;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Session lifecycle eviction — Stage 4, increment 6a. When the owning HTTP session ends the container
 * calls {@link MedleySession#valueUnbound}, which must release the whole component tree with
 * {@code onDestroy} cascaded to every instance.
 */
class MedleySessionLifecycleTest {

    private MedleySession newSession(AnnotationConfigApplicationContext ctx) {
        ctx.register(EvictableChildComponent.class);
        ctx.refresh();
        ComponentRegistry components = new ComponentRegistry(ctx.getBeanFactory());
        return new MedleySession(new TemplateRegistry("templates/medley/", components), 0);
    }

    @Test
    void httpSessionEndDestroysTheWholeTree() {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            MedleySession session = newSession(ctx);
            session.mountChild("root.1::evictable", "evictable", Map.of("start", 1), 1);
            session.mountChild("root.1::evictable.0::evictable", "evictable", Map.of("start", 2), 2);
            EvictableChildComponent a =
                    (EvictableChildComponent) session.get("root.1::evictable").component();
            EvictableChildComponent b =
                    (EvictableChildComponent) session.get("root.1::evictable.0::evictable").component();

            // The container fires this when the HTTP session is invalidated or times out.
            session.valueUnbound(new HttpSessionBindingEvent(mock(HttpSession.class), "medley"));

            assertThat(session.contains("root.1::evictable")).isFalse();
            assertThat(session.contains("root.1::evictable.0::evictable")).isFalse();
            assertThat(a.getDestroyCalls()).isEqualTo(1);
            assertThat(b.getDestroyCalls()).isEqualTo(1);
        }
    }

    @Test
    void destroyAllIsIdempotent() {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            MedleySession session = newSession(ctx);
            session.mountChild("root.1::evictable", "evictable", Map.of("start", 1), 1);
            EvictableChildComponent a =
                    (EvictableChildComponent) session.get("root.1::evictable").component();

            session.destroyAll();
            session.destroyAll(); // second call must not re-fire onDestroy or throw

            assertThat(session.contains("root.1::evictable")).isFalse();
            assertThat(a.getDestroyCalls()).isEqualTo(1);
        }
    }
}
