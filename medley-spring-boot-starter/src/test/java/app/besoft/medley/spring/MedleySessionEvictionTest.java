package app.besoft.medley.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.besoft.medley.core.diff.Patch;
import app.besoft.medley.spring.fixtures.EvictableChildComponent;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Unit-level proof of 4b.3b eviction + cap in {@link MedleySession}. Eviction is patch-driven: a child
 * covered by a structural {@code Replace}/{@code Remove} and not (re)mounted this pass is freed
 * (cascading {@code onDestroy}); a child touched this pass is spared. Lightweight wiring, mirroring
 * {@link MedleySessionCascadeTest} — no full {@code @SpringBootTest}.
 */
class MedleySessionEvictionTest {

    private MedleySession newSession(AnnotationConfigApplicationContext ctx, int cap) {
        ctx.register(EvictableChildComponent.class);
        ctx.refresh();
        ComponentRegistry components = new ComponentRegistry(ctx.getBeanFactory());
        TemplateRegistry templates = new TemplateRegistry("templates/medley/", components);
        return new MedleySession(templates, cap);
    }

    private static List<Patch> replace(String id) {
        return List.of(new Patch.Replace(id, "<medley-placeholder hidden></medley-placeholder>"));
    }

    @Test
    void structurallyRemovedChildIsEvictedWithOnDestroy() {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            MedleySession session = newSession(ctx, 0);
            session.mountChild("root.1::evictable", "evictable", Map.of("start", 1), 1);
            EvictableChildComponent child =
                    (EvictableChildComponent) session.get("root.1::evictable").component();

            session.resetRenderCycle();                 // new pass: the boundary is not re-mounted
            session.evictByPatches(replace("root.1"));  // *if false -> Replace host->placeholder

            assertThat(session.contains("root.1::evictable")).isFalse();
            assertThat(child.getDestroyCalls()).isEqualTo(1);
        }
    }

    @Test
    void childReMountedThisPassIsSpared() {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            MedleySession session = newSession(ctx, 0);
            session.mountChild("root.1::evictable", "evictable", Map.of("start", 1), 1);
            EvictableChildComponent child =
                    (EvictableChildComponent) session.get("root.1::evictable").component();

            session.resetRenderCycle();
            session.mountChild("root.1::evictable", "evictable", Map.of("start", 1), 1); // touched again
            session.evictByPatches(replace("root.1")); // e.g. *if true: Replace introduced the boundary

            assertThat(session.contains("root.1::evictable")).isTrue();
            assertThat(child.getDestroyCalls()).isZero();
        }
    }

    @Test
    void unrelatedStructuralPatchDoesNotEvict() {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            MedleySession session = newSession(ctx, 0);
            session.mountChild("root.1::evictable", "evictable", Map.of("start", 1), 1);

            session.resetRenderCycle();
            session.evictByPatches(replace("root.2")); // sibling slot, not an ancestor of the child

            assertThat(session.contains("root.1::evictable")).isTrue();
        }
    }

    @Test
    void evictionCascadesToDescendants() {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            MedleySession session = newSession(ctx, 0);
            session.mountChild("root.1::evictable", "evictable", Map.of("start", 1), 1);
            session.mountChild("root.1::evictable.0::evictable", "evictable", Map.of("start", 2), 2);
            EvictableChildComponent parent =
                    (EvictableChildComponent) session.get("root.1::evictable").component();
            EvictableChildComponent grandchild =
                    (EvictableChildComponent) session.get("root.1::evictable.0::evictable").component();

            session.resetRenderCycle();
            session.evictByPatches(replace("root.1")); // removing the outer boundary

            assertThat(session.contains("root.1::evictable")).isFalse();
            assertThat(session.contains("root.1::evictable.0::evictable")).isFalse();
            assertThat(parent.getDestroyCalls()).isEqualTo(1);
            assertThat(grandchild.getDestroyCalls()).isEqualTo(1);
        }
    }

    @Test
    void removeCascadesToDescendants() {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            MedleySession session = newSession(ctx, 0);
            session.mountChild("root.1::evictable", "evictable", Map.of("start", 1), 1);
            session.mountChild("root.1::evictable.0::evictable", "evictable", Map.of("start", 2), 2);

            session.remove("root.1::evictable");

            assertThat(session.contains("root.1::evictable")).isFalse();
            assertThat(session.contains("root.1::evictable.0::evictable")).isFalse();
        }
    }

    @Test
    void capExceededThrowsOnChildMount() {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            MedleySession session = newSession(ctx, 1);
            session.mountChild("root.1::evictable", "evictable", Map.of("start", 1), 1); // size 0 -> ok
            assertThatThrownBy(() ->
                    session.mountChild("root.2::evictable", "evictable", Map.of("start", 2), 1))
                    .isInstanceOf(MedleyCapacityExceededException.class);
        }
    }
}
