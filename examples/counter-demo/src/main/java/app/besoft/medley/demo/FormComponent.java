package app.besoft.medley.demo;

import java.util.HashMap;
import java.util.Map;

import app.besoft.medley.core.component.Annotations.Action;
import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.State;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.spring.MedleyRoute;

/**
 * Demonstrates the component library / partials (Stage 4, increment 4) at {@code /form}.
 *
 * <p>The template reuses one {@code <medley-partial name="field">} twice — passing a distinct
 * label, an owner action to bind {@code @input} to (rewritten from the fragment's {@code onChange}
 * param), and a bound {@code error} — composing input-value binding (Inc 2) and validation (Inc 3)
 * without duplicating markup. No child component, no separate state, no wire change.</p>
 */
@MedleyRoute("/form")
@MedleyComponent("form")
public class FormComponent extends Component {

    @State String first = "";
    @State String last = "";
    @State boolean done = false;
    @State Map<String, String> errors = new HashMap<>();

    @Action void setFirst(String value) { this.first = value == null ? "" : value; done = false; }

    @Action void setLast(String value) { this.last = value == null ? "" : value; done = false; }

    @Action void submit() {
        errors.clear();
        if (first.trim().isEmpty()) errors.put("first", "Wymagane.");
        if (last.trim().isEmpty()) errors.put("last", "Wymagane.");
        done = errors.isEmpty();
    }

    public boolean isDone() { return done; }
}
