package app.besoft.medley.demo;

import app.besoft.medley.core.component.Annotations.Action;
import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.State;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.spring.MedleyRoute;

/**
 * A page at {@code /editor} demoing <b>child→parent callbacks</b>. It nests a {@link TextEditorComponent}
 * and binds that child's {@code save} output to its own {@link #onSave} action on the boundary:
 * {@code <medley-component name="text-editor" @save="onSave($event)">}. When the editor emits, this
 * parent records the value and re-renders — the child called <em>up</em>, with no new wire message.
 */
@MedleyRoute("/editor")
@MedleyComponent("editor-page")
public class EditorPageComponent extends Component {

    @State String lastSaved = "(nothing yet)";
    @State int saveCount = 0;

    @Action
    void onSave(String text) {
        this.lastSaved = text;
        this.saveCount++;
    }

    public String getLastSaved() { return lastSaved; }
    public int getSaveCount() { return saveCount; }
}
