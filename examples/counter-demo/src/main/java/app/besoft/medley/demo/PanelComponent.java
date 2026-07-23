package app.besoft.medley.demo;

import app.besoft.medley.core.component.Annotations.Action;
import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.Param;
import app.besoft.medley.core.component.Annotations.State;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.spring.MedleyChild;

/**
 * A wrapper/layout child built on the default slot (Stage 6, increment 6.2): it owns the frame, the
 * heading and its own {@code @State}, while the content written between its {@code <medley-component>}
 * tags belongs to — and is patched by — the parent. Template: {@code templates/medley/panel.html}.
 */
@MedleyChild
@MedleyComponent("panel")
public class PanelComponent extends Component {

    @Param String title = "Panel";
    @State int folds;

    @Action void fold() { folds++; }

    public String getTitle() { return title; }
    public int getFolds() { return folds; }
}
