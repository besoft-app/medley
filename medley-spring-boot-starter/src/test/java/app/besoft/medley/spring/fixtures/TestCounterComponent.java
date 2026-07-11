package app.besoft.medley.spring.fixtures;

import app.besoft.medley.core.component.Annotations.Action;
import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.State;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.spring.MedleyRoute;

/**
 * Test fixture mirroring the counter demo: a routed component whose template is
 * {@code templates/medley/counter.html} on the test classpath. No {@code @Scope} here —
 * prototype scope is carried by {@code @MedleyRoute}, which is exactly what the tests verify.
 */
@MedleyRoute("/counter")
@MedleyComponent("counter")
public class TestCounterComponent extends Component {

    @State int count = 0;
    String label = "Clicks";

    @Action void increment() { count++; }
    @Action void decrement() { if (count > 0) count--; }
    @Action void reset() { count = 0; }

    public int getCount() { return count; }
    public String getLabel() { return label; }
}
