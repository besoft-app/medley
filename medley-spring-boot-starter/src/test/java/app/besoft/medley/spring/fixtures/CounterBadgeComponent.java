package app.besoft.medley.spring.fixtures;

import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.Param;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.spring.MedleyChild;

/**
 * Test fixture: a reusable display-only child component (Stage 4, increment 4b.1). Nested via
 * {@code <medley-component name="counter-badge" :start="...">}; its {@code start} {@code @Param} is
 * injected from the parent. Template: {@code templates/medley/counter-badge.html}.
 */
@MedleyChild
@MedleyComponent("counter-badge")
public class CounterBadgeComponent extends Component {

    @Param int start = 0;

    public int getStart() { return start; }
}
