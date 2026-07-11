package app.besoft.medley.spring.fixtures;

import app.besoft.medley.core.component.Annotations.Action;
import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.State;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.spring.MedleyRoute;

/**
 * Test fixture for input-value binding (Stage 4, increment 2). The template
 * {@code templates/medley/search.html} binds a text input's value to {@link #setQuery(String)}
 * and a checkbox's checked state to {@link #setActive(boolean)}. Inputs are <em>uncontrolled</em>
 * (no {@code :value} binding back to the same field), so typing only patches the echo label —
 * never the input — which keeps the steady-state diff minimal.
 */
@MedleyRoute("/search")
@MedleyComponent("search")
public class SearchComponent extends Component {

    @State String query = "";
    @State boolean active = false;

    @Action void setQuery(String value) { this.query = value; }
    @Action void setActive(boolean checked) { this.active = checked; }

    public String getQuery() { return query; }
    public boolean isActive() { return active; }
}
