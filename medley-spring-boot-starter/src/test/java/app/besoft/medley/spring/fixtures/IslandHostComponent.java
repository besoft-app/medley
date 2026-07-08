package app.besoft.medley.spring.fixtures;

import app.besoft.medley.core.component.Annotations.Action;
import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.State;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.spring.MedleyRoute;

/**
 * Test fixture: a component hosting a {@code <medley-island name="ticker">} whose prop is bound to
 * {@code @State value}. Template: {@code templates/medley/island-host.html}. Routed at /island so
 * SSR of an island can be asserted; also mounted directly in handler tests.
 */
@MedleyRoute("/island")
@MedleyComponent("island-host")
public class IslandHostComponent extends Component {

    @State int value = 0;

    @Action void bump() { value++; }

    /** Mutated by the ticker island's commit (see {@link TickerIsland}). */
    void setValue(int v) { value = v; }

    public int getValue() { return value; }
}
