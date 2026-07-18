package ${package};

import app.besoft.medley.core.component.Annotations.Action;
import app.besoft.medley.core.component.Annotations.Param;
import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.State;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.spring.MedleyRoute;

/**
 * A counter mounted at {@code /}. Each page load gets a fresh instance (prototype scope, carried by
 * {@code @MedleyRoute}) with its own state. In a real component you would {@code @Autowired}
 * repositories/services and use them directly in actions — no REST layer in between.
 */
@MedleyRoute("/")
@MedleyComponent("counter")
public class CounterComponent extends Component {

    @State int count = 0;
    @Param String label = "Clicks";

    @Action void increment() { count++; }
    @Action void decrement() { if (count > 0) count--; }
    @Action void reset() { count = 0; }

    public int getCount() { return count; }
    public String getLabel() { return label; }
}
