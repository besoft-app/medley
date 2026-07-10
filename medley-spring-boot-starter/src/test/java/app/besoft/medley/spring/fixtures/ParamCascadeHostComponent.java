package app.besoft.medley.spring.fixtures;

import app.besoft.medley.core.component.Annotations.Action;
import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.State;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.spring.MedleyRoute;

/**
 * Test fixture: a routed host that passes its own {@code @State} down to an {@link EchoParamComponent}
 * child as a bound {@code :label} (Stage 4, increment 4b.3a). The {@code relabel} action changes the
 * host state, which must cascade a single re-render into the child. The host does not render the label
 * itself, so the child patch is the only visible effect. Template: {@code templates/medley/cascade-host.html}.
 */
@MedleyRoute("/cascade")
@MedleyComponent("cascade-host")
public class ParamCascadeHostComponent extends Component {

    @State String label = "a";

    @Action
    public void relabel() {
        label = "b";
    }

    public String getLabel() { return label; }
}
