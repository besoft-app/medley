package app.besoft.medley.demo;

import app.besoft.medley.core.component.Annotations.Action;
import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.State;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.spring.MedleyRoute;

import java.util.ArrayList;
import java.util.List;

/**
 * Demonstrates keyed {@code *for} end-to-end (Stage 4, increment 1) at {@code /table}.
 *
 * <p>Add/remove/in-place-update each emit a single minimal patch because the {@code <tbody>} is
 * authored whitespace-tight, so every child is a keyed {@code <tr>} and the differ's keyed
 * reconciliation engages. NOTE: there is intentionally no "reorder" action — reorder needs a
 * {@code move} wire op that does not exist yet (a pure reorder is currently a diff no-op), and is
 * deferred to a later increment.</p>
 */
@MedleyRoute("/table")
@MedleyComponent("table")
public class TableComponent extends Component {

    public record Row(String id, String name, int qty) {}

    private int seq = 3;

    @State List<Row> rows = new ArrayList<>(List.of(
            new Row("1", "Widżet", 2),
            new Row("2", "Sprocket", 5),
            new Row("3", "Gadżet", 1)));

    @Action void addRow() {
        seq++;
        rows.add(new Row(String.valueOf(seq), "Pozycja #" + seq, 1));
    }

    @Action void removeFirst() {
        if (!rows.isEmpty()) rows.remove(0);
    }

    /** In-place update of the first row (same key) — emits a single text patch. */
    @Action void bumpFirst() {
        if (!rows.isEmpty()) {
            Row r = rows.get(0);
            rows.set(0, new Row(r.id(), r.name(), r.qty() + 1));
        }
    }

    public List<Row> getRows() { return rows; }

    public boolean isEmpty() { return rows.isEmpty(); }
}
