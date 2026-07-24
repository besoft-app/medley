package app.besoft.medley.demo;

import app.besoft.medley.core.component.Annotations.Action;
import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.State;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.spring.MedleyChild;

/**
 * A card wrapper built on named slots (Stage 6, increment 6.3): a "header" slot (with a fallback) and a
 * default body slot, plus its own collapse {@code @State}. The header and body belong to the parent; the
 * collapse toggle is the child's. Template: {@code templates/medley/card.html}.
 */
@MedleyChild
@MedleyComponent("card")
public class CardComponent extends Component {

    @State boolean collapsed = false;

    @Action void toggle() { collapsed = !collapsed; }

    public boolean isCollapsed() { return collapsed; }
}
