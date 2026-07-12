package app.besoft.medley.spring.fixtures;

import app.besoft.medley.core.component.Annotations.Action;
import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.Output;
import app.besoft.medley.core.component.Annotations.State;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.core.component.EventEmitter;
import app.besoft.medley.spring.MedleyChild;

/**
 * Test fixture: a nested child that calls back up to its parent. Holds a draft in {@code @State text};
 * its {@code keep} {@code @Action} fires the {@code @Output save} emitter, which the framework routes
 * to the parent's bound action with the current text. Template:
 * {@code templates/medley/callback-editor.html}.
 */
@MedleyChild
@MedleyComponent("callback-editor")
public class CallbackEditorComponent extends Component {

    @State String text = "";
    @Output EventEmitter save;

    @Action
    public void type(String value) {
        this.text = value;
    }

    @Action
    public void keep() {
        save.emit(text);
    }
}
