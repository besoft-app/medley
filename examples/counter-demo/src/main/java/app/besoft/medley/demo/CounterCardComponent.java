package app.besoft.medley.demo;

import app.besoft.medley.core.component.Annotations.Action;
import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.Param;
import app.besoft.medley.core.component.Annotations.State;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.spring.MedleyChild;

/**
 * A stateful nested child (Stage 4, increment 4b.2), mounted by {@link DashboardComponent} via
 * {@code <medley-component name="counter-card" :start="…" :label="…">}. Each placement is its own
 * instance with its own {@code @State count}, seeded from the {@code start} {@code @Param}; its
 * {@code increment} action routes straight to that child and diffs only its own subtree, so clicking
 * one card never touches the other card or the host.
 */
@MedleyChild
@MedleyComponent("counter-card")
public class CounterCardComponent extends Component {

    @Param int start = 0;
    @Param String label = "Licznik";
    @State int count;

    @Override public void onInit() { this.count = start; }

    @Action void increment() { count++; }
    @Action void decrement() { if (count > 0) count--; }

    public int getCount() { return count; }
    public String getLabel() { return label; }
}
