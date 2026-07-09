package app.besoft.medley.spring.fixtures;

import app.besoft.medley.core.component.Annotations.Action;
import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.State;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.spring.MedleyRoute;

import java.util.ArrayList;
import java.util.List;

/**
 * Test fixture: a keyed list routed at /table. Template {@code templates/medley/table.html} is
 * authored whitespace-tight so every {@code <tbody>} child is a keyed {@code <tr>}.
 */
@MedleyRoute("/table")
@MedleyComponent("table")
public class TableHostComponent extends Component {

    public record Row(String id, String name) {}

    private int seq = 2;

    @State List<Row> rows = new ArrayList<>(List.of(new Row("1", "one"), new Row("2", "two")));

    @Action void addRow() {
        seq++;
        rows.add(new Row(String.valueOf(seq), "row" + seq));
    }

    @Action void removeFirst() {
        if (!rows.isEmpty()) rows.remove(0);
    }

    public List<Row> getRows() { return rows; }
}
