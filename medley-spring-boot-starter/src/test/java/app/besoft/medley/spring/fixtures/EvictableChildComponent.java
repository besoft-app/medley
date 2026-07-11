package app.besoft.medley.spring.fixtures;

import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.Param;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.spring.MedleyChild;

/**
 * Test fixture: a nested child that counts its {@code onDestroy} calls, so an eviction test can assert
 * the lifecycle hook fired exactly once when the child's boundary was removed (Stage 4, increment
 * 4b.3b). Template: {@code templates/medley/evictable.html}.
 */
@MedleyChild
@MedleyComponent("evictable")
public class EvictableChildComponent extends Component {

    @Param int start = 0;
    int destroyCalls = 0;

    @Override
    public void onDestroy() {
        destroyCalls++;
    }

    public int getDestroyCalls() { return destroyCalls; }
}
