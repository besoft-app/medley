package app.besoft.medley.spring;

import static org.assertj.core.api.Assertions.assertThat;

import app.besoft.medley.core.component.ComponentInstance;
import app.besoft.medley.core.diff.Patch;
import app.besoft.medley.core.diff.PatchMerge;
import app.besoft.medley.spring.fixtures.CallbackEditorComponent;
import app.besoft.medley.spring.fixtures.CallbackParentComponent;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Unit-level proof of child→parent callbacks in {@link MedleySession}. A child's {@code @Output}
 * emitter, bound on the {@code <medley-component>} boundary to a parent action, invokes that action
 * <em>in-process</em> when the child fires it; the owner's resulting patches land in the cascade and
 * are drained + merged with the child's own — one response, no new wire message, no double emission.
 * Mirrors {@link MedleySessionCascadeTest} (a bean factory + registries, no full {@code @SpringBootTest}).
 */
class MedleySessionCallbackTest {

    /** The editor boundary is the second child of the parent root (span at root.0, boundary at root.1). */
    private static final String CHILD_ID = "root.1::callback-editor";

    private MedleySession newSession(AnnotationConfigApplicationContext ctx) {
        ctx.register(CallbackEditorComponent.class);
        ctx.refresh();
        ComponentRegistry components = new ComponentRegistry(ctx.getBeanFactory());
        TemplateRegistry templates = new TemplateRegistry("templates/medley/", components);
        return new MedleySession(templates);
    }

    private CallbackParentComponent mountTree(MedleySession session) {
        CallbackParentComponent parent = new CallbackParentComponent();
        session.mount("root", parent).renderInitialHtml(); // renders parent -> mounts + wires the child
        return parent;
    }

    /** Commit a draft into the child through its own {@code type} action, so its rendered tree is up to
     *  date (realistic: the user typed before clicking save) and a later {@code keep} produces no own
     *  patch. Clears the render cycle afterwards. */
    private void typeInto(MedleySession session, ComponentInstance childInst, String text) {
        session.resetRenderCycle();
        childInst.invokeAction("type", text);
        session.drainCascade();
    }

    @Test
    void childEmitInvokesBoundParentActionAndMergesExactlyOnePatch() {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            MedleySession session = newSession(ctx);
            CallbackParentComponent parent = mountTree(session);

            ComponentInstance childInst = session.get(CHILD_ID);
            assertThat(childInst).as("editor child mounted at the boundary").isNotNull();
            typeInto(session, childInst, "hello");

            session.resetRenderCycle();
            List<Patch> childPatches = childInst.invokeAction("keep"); // fires @Output save up to parent
            List<Patch> merged = PatchMerge.mergeWithSuppression(childPatches, session.drainCascade());

            assertThat(parent.getLastSaved())
                    .as("the bound owner action ran with the emitted payload")
                    .isEqualTo("hello");
            assertThat(merged)
                    .as("only the parent's single text patch — the child emitted no own change (no double emission)")
                    .hasSize(1);
            assertThat(merged.get(0)).isInstanceOf(Patch.SetText.class);
            assertThat(((Patch.SetText) merged.get(0)).value()).isEqualTo("hello");
        }
    }

    @Test
    void emitterIsReusableAcrossSuccessiveCalls() {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            MedleySession session = newSession(ctx);
            CallbackParentComponent parent = mountTree(session);
            ComponentInstance childInst = session.get(CHILD_ID);

            typeInto(session, childInst, "a");
            session.resetRenderCycle();
            childInst.invokeAction("keep");
            assertThat(parent.getLastSaved()).isEqualTo("a");

            typeInto(session, childInst, "b");
            session.resetRenderCycle();
            List<Patch> merged = PatchMerge.mergeWithSuppression(
                    childInst.invokeAction("keep"), session.drainCascade());
            assertThat(parent.getLastSaved()).isEqualTo("b");
            assertThat(merged).hasSize(1);
            assertThat(((Patch.SetText) merged.get(0)).value()).isEqualTo("b");
        }
    }
}
