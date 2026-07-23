package app.besoft.medley.spring.fixtures;

import app.besoft.medley.core.component.Annotations.Action;
import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.State;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.spring.MedleyRoute;

/**
 * Test fixture: a routed parent that projects its own {@code @State} into a {@link SlotPanelComponent}
 * boundary's body (Stage 6, increment 6.2). {@code bump} changes only projected content, so the single
 * resulting patch must be addressed in the PARENT's id-space even though the DOM node lives inside the
 * child. Template: {@code templates/medley/slot-host.html}.
 */
@MedleyRoute("/panel")
@MedleyComponent("slot-host")
public class SlotHostComponent extends Component {

    @State int clicks = 0;
    /** Parent state that is NOT projected — rendered after the boundary, so its patch proves a parent
     *  action on non-projected state never reaches the child (root.2, keeping the boundary at root.1). */
    @State int notes = 0;

    @Action
    public void bump() {
        clicks++;
    }

    @Action
    public void nudge() {
        notes++;
    }

    public int getClicks() { return clicks; }
    public int getNotes() { return notes; }
}
