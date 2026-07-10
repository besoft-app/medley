package app.besoft.medley.demo;

import app.besoft.medley.core.component.Annotations.Action;
import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.State;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.spring.MedleyRoute;

/**
 * A dashboard at {@code /cards} that nests two independent {@link CounterCardComponent} children
 * (Stage 4, increment 4b.2). Each card keeps its own {@code @State}; the host's own {@code refresh}
 * action re-renders the host without disturbing either card (the boundary is diff-opaque), which is
 * the visible payoff of nested server components with persistent state.
 */
@MedleyRoute("/cards")
@MedleyComponent("dashboard")
public class DashboardComponent extends Component {

    @State int views = 1;

    @Action void refresh() { views++; }

    public int getViews() { return views; }
}
