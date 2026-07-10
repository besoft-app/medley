package app.besoft.medley.spring.fixtures;

import app.besoft.medley.core.component.Annotations.Action;
import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.State;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.spring.MedleyRoute;

/**
 * Test fixture: a routed host nesting a stateful {@link CounterCardComponent} (Stage 4, increment
 * 4b.2). It has its own {@code bump} {@code @Action}; a host re-render must leave the child's DOM and
 * {@code @State} untouched (the boundary is diff-opaque), while a child action leaves the host
 * untouched. {@code seed} feeds the child's {@code start} {@code @Param}. Template:
 * {@code templates/medley/card-host.html}.
 */
@MedleyRoute("/cards")
@MedleyComponent("card-host")
public class CardHostComponent extends Component {

    @State int seed = 10;
    @State int hostClicks = 0;

    @Action
    public void bump() {
        hostClicks++;
    }

    public int getSeed() {
        return seed;
    }

    public int getHostClicks() {
        return hostClicks;
    }
}
