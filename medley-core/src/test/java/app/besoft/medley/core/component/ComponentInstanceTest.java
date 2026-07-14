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

    /** Typed actions exercise value-binding: the client always sends args as JSON scalars, and the
        core must coerce each to the {@code @Action}'s declared parameter type. */
    @Annotations.MedleyComponent("form")
    static class Form extends Component {
        @Annotations.State String query = "";
        @Annotations.State boolean active = false;
        @Annotations.State int qty = 0;
        @Annotations.Action void setQuery(String value) { this.query = value; }
        @Annotations.Action void setActive(boolean checked) { this.active = checked; }
        @Annotations.Action void setQty(int value) { this.qty = value; }
    }

    // Whitespace-tight so a query change is a single text patch on the label.
    private static final String TPL_FORM = "<div><span>{{ query }}</span></div>";

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
        // Interpolated values are rendered inside an addressable <medley-text> host, so the text a
        // patch can update is reachable from the client; the literal between them stays bare text.
        assertTrue(html.contains(">n</medley-text>: <medley-text"), html);
        assertTrue(html.contains(">0</medley-text>"), html);
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
    void stringArgFromClientCoercesToIntParam() {
        // A text/number input sends its value as a JSON string; setQty declares int.
        Form f = new Form();
        ComponentInstance inst = new ComponentInstance("root", f, TemplateRenderer.of(TPL_FORM));
        inst.renderInitialHtml();
        inst.invokeAction("setQty", "3");
        assertEquals(3, f.qty, "String '3' must coerce to int param");
    }

    @Test
    void stringArgFromClientCoercesToBooleanParam() {
        Form f = new Form();
        ComponentInstance inst = new ComponentInstance("root", f, TemplateRenderer.of(TPL_FORM));
        inst.renderInitialHtml();
        inst.invokeAction("setActive", "true");
        assertTrue(f.active, "String 'true' must coerce to boolean param");
    }

    @Test
    void realBooleanArgPassesThroughToBooleanParam() {
        // A checkbox sends $checked as a real JSON boolean; it must not be lost in coercion.
        Form f = new Form();
        ComponentInstance inst = new ComponentInstance("root", f, TemplateRenderer.of(TPL_FORM));
        inst.renderInitialHtml();
        inst.invokeAction("setActive", Boolean.TRUE);
        assertTrue(f.active);
    }

    @Test
    void valueBindingUpdatesLabelInSinglePatch() {
        // Uncontrolled input: value feeds @State, a {{ query }} label echoes it. Steady-state
        // typing must stay minimal — exactly one text patch on the label, never on the input.
        Form f = new Form();
        ComponentInstance inst = new ComponentInstance("root", f, TemplateRenderer.of(TPL_FORM));
        inst.renderInitialHtml();
        inst.invokeAction("setQuery", "ab");
        List<Patch> patches = inst.invokeAction("setQuery", "cd");
        assertEquals(1, patches.size(), "steady-state value binding must be a single patch");
        assertTrue(patches.get(0) instanceof Patch.SetText);
        assertEquals("cd", ((Patch.SetText) patches.get(0)).value());
        assertEquals("cd", f.query);
    }

    @Test
    void arityMismatchThrows() {
        Form f = new Form();
        ComponentInstance inst = new ComponentInstance("root", f, TemplateRenderer.of(TPL_FORM));
        inst.renderInitialHtml();
        // setQuery declares one param; sending none is a handled failure, not a crash.
        assertThrows(TemplateException.class, () -> inst.invokeAction("setQuery"));
    }

    @Test
    void unparseableValueThrows() {
        Form f = new Form();
        ComponentInstance inst = new ComponentInstance("root", f, TemplateRenderer.of(TPL_FORM));
        inst.renderInitialHtml();
        assertThrows(TemplateException.class, () -> inst.invokeAction("setQty", "not-a-number"));
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
