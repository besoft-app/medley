package app.besoft.medley.spring.fixtures;

import app.besoft.medley.core.component.Annotations.Action;
import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.Param;
import app.besoft.medley.core.component.Annotations.State;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.spring.MedleyChild;

/**
 * Test fixture: a <em>stateful</em> nested child (Stage 4, increment 4b.2). Nested via
 * {@code <medley-component name="counter-card" :start="...">}; its {@code start} {@code @Param} seeds
 * the {@code count} {@code @State} in {@code onInit}. Its {@code increment} {@code @Action} is routed
 * straight to this child instance over the WebSocket (componentId = the child instance id), and the
 * child diffs its own subtree — a single text patch — without touching the parent. Template:
 * {@code templates/medley/counter-card.html}.
 */
@MedleyChild
@MedleyComponent("counter-card")
public class CounterCardComponent extends Component {

    @Param int start = 0;
    @State int count;

    @Override
    public void onInit() {
        this.count = start;
    }

    @Action
    public void increment() {
        count++;
    }

    public int getCount() {
        return count;
    }
}
