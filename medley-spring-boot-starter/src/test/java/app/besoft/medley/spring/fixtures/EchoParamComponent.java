package app.besoft.medley.spring.fixtures;

import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.Param;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.spring.MedleyChild;

/**
 * Test fixture: a nested child that just echoes a bound {@code :label} {@code @Param} (Stage 4,
 * increment 4b.3a). Counts {@code onParamChange} calls so a test can assert the hook fires exactly
 * when the parent changes the param. Template: {@code templates/medley/echo-param.html}.
 */
@MedleyChild
@MedleyComponent("echo-param")
public class EchoParamComponent extends Component {

    @Param String label = "";
    int paramChangeCalls = 0;

    @Override
    public void onParamChange() {
        paramChangeCalls++;
    }

    public String getLabel() { return label; }
    public int getParamChangeCalls() { return paramChangeCalls; }
}
