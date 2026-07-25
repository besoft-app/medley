package app.besoft.medley.spring.fixtures;

import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.spring.MedleyRoute;

/**
 * Test fixture (Stage 6.3): fills only the {@link CardComponent}'s default slot, so the card's header
 * shows its own {@code Untitled} fallback (rendered in the child's scope, at a child id). Template:
 * {@code templates/medley/named-slot-plain.html}.
 */
@MedleyRoute("/card-plain")
@MedleyComponent("named-slot-plain")
public class NamedSlotPlainComponent extends Component {
}
