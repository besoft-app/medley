package app.besoft.medley.spring;

import static org.assertj.core.api.Assertions.assertThat;

import app.besoft.medley.core.diff.Patch;
import app.besoft.medley.spring.fixtures.EchoParamComponent;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Unit-level proof of the 4b.3a props-down cascade in {@link MedleySession#mountChild}: a reused child
 * is re-injected + {@code onParamChange}d + re-rendered only when its bound params actually change, and
 * the resulting child patches land in the drainable cascade. Lightweight wiring (a bean factory +
 * registries), mirroring {@link RouteRegistryTest} — no full {@code @SpringBootTest}.
 */
class MedleySessionCascadeTest {

    private static final String CHILD_ID = "root.1::echo-param";

    private MedleySession newSession(AnnotationConfigApplicationContext ctx) {
        ctx.register(EchoParamComponent.class);
        ctx.refresh();
        ComponentRegistry components = new ComponentRegistry(ctx.getBeanFactory());
        TemplateRegistry templates = new TemplateRegistry("templates/medley/", components);
        return new MedleySession(templates);
    }

    @Test
    void unchangedParamsDoNotFireOnParamChangeOrCascade() {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            MedleySession session = newSession(ctx);
            session.mountChild(CHILD_ID, "echo-param", Map.of("label", "a"), 1);
            session.mountChild(CHILD_ID, "echo-param", Map.of("label", "a"), 1); // identical reuse

            assertThat(session.drainCascade()).isEmpty();
            EchoParamComponent child = (EchoParamComponent) session.get(CHILD_ID).component();
            assertThat(child.getParamChangeCalls()).isZero();
        }
    }

    @Test
    void changedParamsFireOnParamChangeAndProduceExactlyOneCascadePatch() {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            MedleySession session = newSession(ctx);
            session.mountChild(CHILD_ID, "echo-param", Map.of("label", "a"), 1);
            session.mountChild(CHILD_ID, "echo-param", Map.of("label", "b"), 1); // param changed

            EchoParamComponent child = (EchoParamComponent) session.get(CHILD_ID).component();
            assertThat(child.getParamChangeCalls()).isEqualTo(1);
            assertThat(child.getLabel()).isEqualTo("b");

            List<Patch> cascade = session.drainCascade();
            assertThat(cascade).hasSize(1);
            assertThat(cascade.get(0)).isInstanceOf(Patch.SetText.class);
            Patch.SetText p = (Patch.SetText) cascade.get(0);
            assertThat(p.id()).isEqualTo(CHILD_ID + ".0");
            assertThat(p.value()).isEqualTo("b");
        }
    }

    @Test
    void resetRenderCycleClearsStaleCascade() {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            MedleySession session = newSession(ctx);
            session.mountChild(CHILD_ID, "echo-param", Map.of("label", "a"), 1);
            session.mountChild(CHILD_ID, "echo-param", Map.of("label", "b"), 1); // buffers a patch

            session.resetRenderCycle();
            assertThat(session.drainCascade()).isEmpty();
        }
    }
}
