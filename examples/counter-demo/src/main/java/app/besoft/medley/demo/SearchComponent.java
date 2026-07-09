package app.besoft.medley.demo;

import app.besoft.medley.core.component.Annotations.Action;
import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.State;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.spring.MedleyRoute;

import java.util.List;

/**
 * Demonstrates input-value binding (Stage 4, increment 2) at {@code /search}.
 *
 * <p>A text input's value is bound to {@link #setQuery(String)} via {@code @input="setQuery($value)"}
 * and a checkbox's checked state to {@link #setActive(boolean)} via {@code @change="setActive($checked)"}.
 * The inputs are <em>uncontrolled</em> — no {@code :value} is echoed back to the same field — so typing
 * only patches the results below, never the field being typed into (minimal-diff preserved).</p>
 */
@MedleyRoute("/search")
@MedleyComponent("search")
public class SearchComponent extends Component {

    private static final List<String> FRUITS = List.of(
            "apple", "apricot", "banana", "blueberry", "cherry", "grape", "grapefruit",
            "lemon", "lime", "mango", "melon", "orange", "peach", "pear", "plum");

    @State String query = "";
    @State boolean caseSensitive = false;

    @Action void setQuery(String value) { this.query = value == null ? "" : value; }

    @Action void setCaseSensitive(boolean checked) { this.caseSensitive = checked; }

    /** Server-side filtered results — recomputed each render from the bound @State. */
    public List<String> getMatches() {
        if (query.isBlank()) return FRUITS;
        String q = caseSensitive ? query : query.toLowerCase();
        return FRUITS.stream()
                .filter(f -> (caseSensitive ? f : f.toLowerCase()).contains(q))
                .toList();
    }

    public String getQuery() { return query; }

    public int getMatchCount() { return getMatches().size(); }
}
