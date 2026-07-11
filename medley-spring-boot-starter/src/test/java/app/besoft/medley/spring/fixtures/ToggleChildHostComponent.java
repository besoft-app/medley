package app.besoft.medley.spring.fixtures;

import app.besoft.medley.core.component.Annotations.Action;
import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.State;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.spring.MedleyRoute;

/**
 * Test fixture: a routed host that shows/hides a stateful {@link CounterCardComponent} child behind an
 * {@code *if} (Stage 4, increment 4b.3b). Toggling the child off structurally removes its boundary
 * (Replace host→placeholder), which must evict the child instance; toggling back on mounts a fresh one
 * (its state starts over). Template: {@code templates/medley/toggle-child-host.html}.
 */
@MedleyRoute("/toggle-child")
@MedleyComponent("toggle-child-host")
public class ToggleChildHostComponent extends Component {

    @State int seed = 10;
    @State boolean show = true;

    @Action
    public void toggle() {
        show = !show;
    }

    public int getSeed() { return seed; }
    public boolean isShow() { return show; }
}
