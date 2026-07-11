package app.besoft.medley.spring.fixtures;

import app.besoft.medley.core.component.Annotations.Action;
import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.State;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.spring.MedleyRoute;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Test fixture: a routed host that renders a keyed {@code *for} of stateful {@link CounterCardComponent}
 * children (Stage 4, increment 4b.3c). Each row mounts its own child at {@code hostId[key]::name}; the
 * loop container is authored whitespace-tight so the differ's keyed reconciliation engages (add → one
 * Insert, remove → eviction, reorder → no-op). Template: {@code templates/medley/card-list-host.html}.
 */
@MedleyRoute("/card-list")
@MedleyComponent("card-list-host")
public class CardListHostComponent extends Component {

    public record Row(String id, int seed) {}

    @State List<Row> rows = new ArrayList<>(List.of(new Row("a", 10), new Row("b", 20)));

    @Action public void addRow() { rows.add(new Row("c", 30)); }

    @Action public void removeRow(String id) { rows.removeIf(r -> r.id().equals(id)); }

    @Action public void reorder() { Collections.reverse(rows); }

    public List<Row> getRows() { return rows; }
}
