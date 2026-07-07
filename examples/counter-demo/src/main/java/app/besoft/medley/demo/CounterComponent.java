package app.besoft.medley.demo;

import app.besoft.medley.core.component.Annotations.Action;
import app.besoft.medley.core.component.Annotations.Param;
import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.State;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.spring.MedleyRoute;

/**
 * A counter mounted at {@code /counter}.
 *
 * <p>{@code @MedleyRoute} carries prototype scope, so each page load gets a fresh instance with
 * its own state — no manual {@code @Scope} needed. In a real component you would
 * {@code @Autowired} repositories/services here and use them directly in {@code onInit()} and
 * actions — no REST layer in between.</p>
 */
@MedleyRoute("/counter")
@MedleyComponent("counter")
public class CounterComponent extends Component {

    @State int count = 0;
    @Param String label = "Kliknięcia";

    @Action void increment() { count++; }
    @Action void decrement() { if (count > 0) count--; }
    @Action void reset() { count = 0; }

    // Getters so the template can read state via getters or fields (both supported).
    public int getCount() { return count; }
    public String getLabel() { return label; }
}
