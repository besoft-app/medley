package app.besoft.medley.demo;

import app.besoft.medley.core.component.Annotations.Action;
import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.Output;
import app.besoft.medley.core.component.Annotations.State;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.core.component.EventEmitter;
import app.besoft.medley.spring.MedleyChild;

/**
 * A reusable editor child (child→parent callbacks demo), nested by {@link EditorPageComponent}. It
 * keeps a private {@code draft} in {@code @State} (fed by the Inc-2 input binding {@code @input=
 * "type($value)"}); clicking Save runs {@link #commit}, which fires the {@code @Output save} emitter.
 * The framework routes that emit to whatever owner action the parent bound on the boundary. Template:
 * {@code templates/medley/text-editor.html}.
 */
@MedleyChild
@MedleyComponent("text-editor")
public class TextEditorComponent extends Component {

    @State String draft = "";
    @Output EventEmitter save;

    @Action
    void type(String value) {
        this.draft = value;
    }

    @Action
    void commit() {
        save.emit(draft);
    }

    public String getDraft() { return draft; }
}
