package app.besoft.medley.spring.fixtures;

import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.State;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.spring.MedleyRoute;

/**
 * Test fixture: a routed host that nests {@link CounterBadgeComponent} twice (Stage 4, increment
 * 4b.1), passing a distinct {@code start} to each. Template: {@code templates/medley/nested-host.html}.
 */
@MedleyRoute("/nested")
@MedleyComponent("nested-host")
public class NestedHostComponent extends Component {

    @State int n = 5;

    public int getN() { return n; }
}
