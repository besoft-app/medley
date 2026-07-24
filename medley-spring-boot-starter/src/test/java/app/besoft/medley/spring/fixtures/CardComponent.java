package app.besoft.medley.spring.fixtures;

import app.besoft.medley.core.component.Annotations.Action;
import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.State;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.spring.MedleyChild;

/**
 * Test fixture (Stage 6.3): a wrapper child with a named "header" slot (fallback "Untitled") and a
 * default body slot, plus its own {@code @State} so one route proves named slots coexist with child
 * isolation. Template: {@code templates/medley/card.html}.
 */
@MedleyChild
@MedleyComponent("card")
public class CardComponent extends Component {

    @State int seen = 0;

    @Action
    public void mark() {
        seen++;
    }

    public int getSeen() { return seen; }
}
