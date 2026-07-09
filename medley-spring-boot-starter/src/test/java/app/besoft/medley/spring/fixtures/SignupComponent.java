package app.besoft.medley.spring.fixtures;

import java.util.HashMap;
import java.util.Map;

import app.besoft.medley.core.component.Annotations.Action;
import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.State;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.spring.MedleyRoute;

/**
 * Test fixture for form validation (Stage 4, increment 3). Validation is imperative in
 * {@link #submit()}; errors live in a plain {@code @State Map} surfaced via {@code *if} + {{ }}.
 * {@code submit()} is the server-side trust boundary — it refuses to commit invalid state even if a
 * client sends the action directly (a browser might disable the button; a raw socket can't be trusted).
 */
@MedleyRoute("/signup")
@MedleyComponent("signup")
public class SignupComponent extends Component {

    @State String name = "";
    @State int submitted = 0;
    @State Map<String, String> errors = new HashMap<>();

    @Action void setName(String value) { this.name = value == null ? "" : value; }

    @Action void submit() {
        errors.clear();
        if (name.trim().length() < 2) {
            errors.put("name", "Name must be at least 2 characters");
        }
        if (errors.isEmpty()) {   // valid -> commit
            submitted++;
            name = "";
        }
    }

    public String getName() { return name; }
    public int getSubmitted() { return submitted; }
    public Map<String, String> getErrors() { return errors; }
}
