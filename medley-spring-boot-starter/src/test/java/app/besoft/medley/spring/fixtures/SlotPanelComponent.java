package app.besoft.medley.spring.fixtures;

import app.besoft.medley.core.component.Annotations.Action;
import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.Param;
import app.besoft.medley.core.component.Annotations.State;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.spring.MedleyChild;

/**
 * Test fixture: a wrapper child with a {@code <medley-slot>} and its own {@code @State}, so one route
 * proves both isolations — a parent action patches only projected content, a child action patches only
 * the child. Template: {@code templates/medley/slot-panel.html}.
 */
@MedleyChild
@MedleyComponent("slot-panel")
public class SlotPanelComponent extends Component {

    @Param String title = "";
    @State int ticks = 0;

    @Action
    public void tick() {
        ticks++;
    }

    public String getTitle() { return title; }
    public int getTicks() { return ticks; }
}
