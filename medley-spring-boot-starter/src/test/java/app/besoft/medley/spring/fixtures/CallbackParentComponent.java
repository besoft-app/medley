package app.besoft.medley.spring.fixtures;

import app.besoft.medley.core.component.Annotations.Action;
import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.State;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.spring.MedleyRoute;

/**
 * Test fixture: a routed parent that receives a child→parent callback. Its
 * {@code <medley-component name="callback-editor" @save="onEditorSave($event)">} boundary binds the
 * child's {@code save} output to {@link #onEditorSave}; when the editor emits, the parent stores the
 * value and re-renders. Template: {@code templates/medley/callback-parent.html}.
 */
@MedleyRoute("/editor")
@MedleyComponent("callback-parent")
public class CallbackParentComponent extends Component {

    @State String lastSaved = "";

    @Action
    public void onEditorSave(String value) {
        this.lastSaved = value;
    }

    public String getLastSaved() {
        return lastSaved;
    }
}
