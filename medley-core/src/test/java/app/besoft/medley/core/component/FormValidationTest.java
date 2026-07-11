package app.besoft.medley.core.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.besoft.medley.core.diff.Patch;
import app.besoft.medley.core.template.TemplateRenderer;
import org.junit.jupiter.api.Test;

/**
 * Form validation (Stage 4, increment 3): imperative validation in an {@code @Action}, error state
 * as a plain {@code @State Map}, surfaced via {@code *if} + {{ }}. No new framework class, no new
 * wire op — this test pins the two things the design promised: a {@code submit()} refuses to commit
 * invalid state, and error appear/clear stays minimal-diff.
 */
class FormValidationTest {

    @Annotations.MedleyComponent("signup")
    static class Signup extends Component {
        @Annotations.State String name = "";
        @Annotations.State int submitted = 0;
        @Annotations.State int hits = 0;
        @Annotations.State Map<String, String> errors = new HashMap<>();

        @Annotations.Action void setName(String value) { this.name = value == null ? "" : value; }
        @Annotations.Action void ping() { hits++; }

        @Annotations.Action void submit() {
            errors.clear();
            if (name.trim().length() < 2) {
                errors.put("name", "Name must be at least 2 characters");
            }
            if (errors.isEmpty()) {   // valid -> commit
                submitted++;
                name = "";
            }
        }

        int getSubmitted() { return submitted; }
        int getHits() { return hits; }
    }

    // Whitespace-tight so each dynamic node maps to a single, predictable patch.
    private static final String TPL =
            "<form><input @input=\"setName($value)\">"
          + "<span *if=\"errors.name\">{{ errors.name }}</span>"
          + "<button @click=\"submit\">Sign up</button>"
          + "<p>submitted: {{ submitted }}</p><p>hits: {{ hits }}</p></form>";

    private static ComponentInstance mount(Signup s) {
        ComponentInstance inst = new ComponentInstance("root", s, TemplateRenderer.of(TPL));
        inst.renderInitialHtml();
        return inst;
    }

    @Test
    void invalidSubmitShowsErrorInSinglePatchAndBlocksCommit() {
        Signup s = new Signup();
        ComponentInstance inst = mount(s);
        inst.invokeAction("setName", "a");            // too short; uncontrolled input -> no patch
        List<Patch> patches = inst.invokeAction("submit");

        assertEquals(1, patches.size(), "error appearing must be a single patch");
        assertTrue(patches.get(0) instanceof Patch.Replace,
                "error span materializes from the *if placeholder via one Replace");
        assertEquals(0, s.getSubmitted(), "invalid submit must not commit");
    }

    @Test
    void validSubmitCommitsAndClearsErrorMinimally() {
        Signup s = new Signup();
        ComponentInstance inst = mount(s);
        inst.invokeAction("setName", "a");
        inst.invokeAction("submit");                  // error shown
        inst.invokeAction("setName", "Ada");          // uncontrolled; error still shown until re-validated
        List<Patch> patches = inst.invokeAction("submit");

        assertEquals(1, s.getSubmitted(), "valid submit must commit exactly once");
        assertEquals(2, patches.size(), "error clears + submitted increments — two necessary patches");
        assertTrue(patches.stream().anyMatch(p -> p instanceof Patch.Replace),
                "error span collapses back to the *if placeholder");
        assertTrue(patches.stream().anyMatch(p -> p instanceof Patch.SetText),
                "submitted counter updates");
    }

    @Test
    void errorNodePresenceDoesNotInflateUnrelatedActionPatch() {
        Signup s = new Signup();
        ComponentInstance inst = mount(s);
        inst.invokeAction("setName", "a");
        inst.invokeAction("submit");                  // error is now visible in the tree
        inst.invokeAction("ping");                    // 0 -> 1 warm-up
        List<Patch> patches = inst.invokeAction("ping"); // steady state 1 -> 2

        assertEquals(1, patches.size(), "an unrelated action stays a single patch despite the error node");
        assertTrue(patches.get(0) instanceof Patch.SetText);
    }
}
