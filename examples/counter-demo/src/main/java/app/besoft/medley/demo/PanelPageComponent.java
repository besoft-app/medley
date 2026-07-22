package app.besoft.medley.demo;

import app.besoft.medley.core.component.Annotations.Action;
import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.State;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.spring.MedleyRoute;

/**
 * Demo of children projection (Stage 6, increment 6.2): this page authors content INSIDE a
 * {@code <medley-component name="panel">} boundary. The projected text and button stay the parent's —
 * clicking the projected button patches only the projected text, while the panel's own button patches
 * only the panel. Template: {@code templates/medley/panel-page.html}.
 */
@MedleyRoute("/panel")
@MedleyComponent("panel-page")
public class PanelPageComponent extends Component {

    @State int clicks;

    @Action void bump() { clicks++; }

    public int getClicks() { return clicks; }
}
