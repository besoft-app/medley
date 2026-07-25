package app.besoft.medley.spring.fixtures;

import app.besoft.medley.core.component.Annotations.Action;
import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.State;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.spring.MedleyRoute;

/**
 * Test fixture (Stage 6.3): fills the {@link CardComponent}'s named header and default body slots.
 * {@code rename} changes the header's projected text — one patch in the PARENT's id-space even though
 * the node lives inside the child's DOM. Template: {@code templates/medley/named-slot-host.html}.
 */
@MedleyRoute("/card")
@MedleyComponent("named-slot-host")
public class NamedSlotHostComponent extends Component {

    @State String title = "First";

    @Action
    public void rename() {
        title = "Second";
    }

    public String getTitle() { return title; }
}
