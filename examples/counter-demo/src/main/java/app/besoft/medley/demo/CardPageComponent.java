package app.besoft.medley.demo;

import app.besoft.medley.core.component.Annotations.Action;
import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.State;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.spring.MedleyRoute;

/**
 * Demo of named slots + fallback (Stage 6, increment 6.3): fills the card's "header" and default body.
 * Renaming patches only the projected header; incrementing patches only the projected figure; the card's
 * own collapse toggle patches only the card. Template: {@code templates/medley/card-page.html}.
 */
@MedleyRoute("/card")
@MedleyComponent("card-page")
public class CardPageComponent extends Component {

    @State String title = "Sales";
    @State int figure = 0;

    @Action void rename() { title = title.equals("Sales") ? "Revenue" : "Sales"; }
    @Action void inc() { figure++; }

    public String getTitle() { return title; }
    public int getFigure() { return figure; }
}
