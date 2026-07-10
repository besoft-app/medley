package app.besoft.medley.spring.fixtures;

import app.besoft.medley.core.component.Annotations.Action;
import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.State;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.spring.MedleyRoute;

/**
 * Test fixture for template partials (Stage 4, increment 4). The template
 * {@code templates/medley/partial-host.html} embeds a {@code <medley-partial name="field">}
 * (loaded from {@code templates/medley/partials/field.html}) and passes {@code onChange="setName"}.
 * The partial's {@code @input="onChange($value)"} is rewritten to the owner's {@link #setName(String)}.
 */
@MedleyRoute("/partials")
@MedleyComponent("partial-host")
public class PartialHostComponent extends Component {

    @State String name = "";

    @Action void setName(String value) { this.name = value == null ? "" : value; }

    public String getName() { return name; }
}
