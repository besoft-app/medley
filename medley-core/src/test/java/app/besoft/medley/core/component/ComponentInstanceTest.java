package app.besoft.medley.core.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import app.besoft.medley.core.diff.Patch;
import app.besoft.medley.core.template.TemplateException;
import app.besoft.medley.core.template.TemplateRenderer;
import org.junit.jupiter.api.Test;

class ComponentInstanceTest {

    @Annotations.MedleyComponent("counter")
    static class Counter extends Component {
        @Annotations.State int count = 0;
        @Annotations.Param String label = "n";
        @Annotations.Action void increment() { count++; }
        @Annotations.Action void decrement() { if (count > 0) count--; }
        @Annotations.Action void reset() { count = 0; }
        boolean initialized = false;
        @Override public void onInit() { initialized = true; }
    }

    private static final String TPL = """
        <div class="counter">
          <span>{{ label }}: {{ count }}</span>
          <button @click="increment">+</button>
          <button @click="reset" *if="count > 0">reset</button>
        </div>
        """;

    private ComponentInstance newCounter() {
        return new ComponentInstance("root", new Counter(), TemplateRenderer.of(TPL));
    }

    @Test
    void initialRenderCallsOnInitAndProducesHtml() {
        Counter c = new Counter();
        ComponentInstance inst = new ComponentInstance("root", c, TemplateRenderer.of(TPL));
        String html = inst.renderInitialHtml();
        assertTrue(c.initialized, "onInit must run on initial render");
        assertTrue(html.contains("data-medley-id=\"root\""));
        assertTrue(html.contains("n: 0"));
        assertTrue(html.contains("data-medley-on-click=\"increment\""));
    }

    @Test
    void incrementProducesMinimalPatch() {
        ComponentInstance inst = newCounter();
        inst.renderInitialHtml();
        // first increment: text changes 0->1 AND the *if reset button appears
        List<Patch> first = inst.invokeAction("increment");
        assertTrue(first.stream().anyMatch(p -> p instanceof Patch.SetText));
        assertTrue(first.stream().anyMatch(p -> p instanceof Patch.Replace),
                "reset button should appear via Replace of the placeholder");

        // second increment: ONLY a text patch — this is the core efficiency claim
        List<Patch> second = inst.invokeAction("increment");
        assertEquals(1, second.size(), "steady-state increment must be a single patch");
        assertTrue(second.get(0) instanceof Patch.SetText);
        assertEquals("2", ((Patch.SetText) second.get(0)).value());
    }

    @Test
    void resetHidesConditionalButton() {
        ComponentInstance inst = newCounter();
        inst.renderInitialHtml();
        inst.invokeAction("increment");
        List<Patch> patches = inst.invokeAction("reset");
        assertTrue(patches.stream().anyMatch(p -> p instanceof Patch.SetText
                && ((Patch.SetText) p).value().equals("0")));
        assertTrue(patches.stream().anyMatch(p -> p instanceof Patch.Replace),
                "reset button should disappear via Replace back to placeholder");
    }

    @Test
    void decrementGuardedByLogic() {
        ComponentInstance inst = newCounter();
        inst.renderInitialHtml();
        // count is 0; decrement should be a no-op -> no patches
        List<Patch> patches = inst.invokeAction("decrement");
        assertTrue(patches.isEmpty(), "no-op action yields no patches");
    }

    @Test
    void unknownActionThrows() {
        ComponentInstance inst = newCounter();
        inst.renderInitialHtml();
        assertThrows(TemplateException.class, () -> inst.invokeAction("nonexistent"));
    }

    @Test
    void onlyAnnotatedMethodsAreCallable() {
        // 'helper' is not @Action, so it must not be invokable from the client path
        @Annotations.MedleyComponent("x")
        class WithHelper extends Component {
            @Annotations.State int n = 0;
            @Annotations.Action void bump() { n++; }
            void helper() { n = 999; } // not an action
        }
        ComponentInstance inst = new ComponentInstance("root", new WithHelper(),
                TemplateRenderer.of("<div>{{ n }}</div>"));
        inst.renderInitialHtml();
        assertThrows(TemplateException.class, () -> inst.invokeAction("helper"));
    }
}
