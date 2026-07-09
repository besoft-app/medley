package app.besoft.medley.demo;

import java.util.HashMap;
import java.util.Map;

import app.besoft.medley.core.component.Annotations.Action;
import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.State;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.spring.MedleyRoute;

/**
 * Demonstrates form validation (Stage 4, increment 3) at {@code /signup}.
 *
 * <p>Validation is plain Java in {@link #submit()}; errors live in a {@code @State Map} and surface
 * through the existing {@code *if} + {{ }} template primitives (the evaluator resolves
 * {@code errors.name} as a map-key lookup). {@code submit()} refuses to commit invalid state — no
 * new dependency, no new template construct, no wire change. Inputs stay uncontrolled, so marking a
 * field invalid never clobbers what the user is typing.</p>
 */
@MedleyRoute("/signup")
@MedleyComponent("signup")
public class SignupComponent extends Component {

    private static final String EMAIL = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

    @State String name = "";
    @State String email = "";
    @State int registered = 0;
    @State Map<String, String> errors = new HashMap<>();

    @Action void setName(String value) { this.name = value == null ? "" : value; }

    @Action void setEmail(String value) { this.email = value == null ? "" : value; }

    @Action void submit() {
        errors.clear();
        if (name.trim().length() < 2) {
            errors.put("name", "Podaj imię (min. 2 znaki).");
        }
        if (!email.matches(EMAIL)) {
            errors.put("email", "Niepoprawny adres e-mail.");
        }
        if (errors.isEmpty()) {   // valid -> commit and reset the form
            registered++;
            name = "";
            email = "";
        }
    }

    public int getRegistered() { return registered; }
}
