package app.besoft.medley.core.component;

import java.util.function.Consumer;

/**
 * A child component's <b>output</b> channel to its parent — the child→parent half of composition.
 *
 * <p>A child declares an {@code @Output EventEmitter} field and fires it from an {@code @Action}:
 * <pre>
 *   {@literal @}Output EventEmitter save;
 *   {@literal @}Action void keep() { save.emit(text); }
 * </pre>
 * A parent binds the output on the {@code <medley-component>} boundary, reusing the {@code @event}
 * grammar, to one of its own actions:
 * <pre>
 *   &lt;medley-component name="editor" :value="draft" {@literal @}save="onEditorSave($event)"&gt;
 * </pre>
 * At mount the framework wires each emitter's sink to invoke that owner action with the emitted
 * payload (see {@link OutputBinder}). Because both components live server-side in the same session,
 * the call is a direct in-process invocation — <b>no new wire message</b>; the owner's resulting
 * patches ride back with the child's in the same response.</p>
 *
 * <p>An output the parent did not bind is a <b>no-op</b> — emitting it does nothing, like an unbound
 * Blazor {@code EventCallback}. The sink is set only by {@link OutputBinder} (same package); user code
 * cannot rebind it.</p>
 */
public final class EventEmitter {

    private Consumer<Object> sink = payload -> {};

    /** Fire the callback up to the parent, passing {@code payload} to the bound owner action. */
    public void emit(Object payload) {
        sink.accept(payload);
    }

    /** Fire the callback up to the parent with no payload (for a zero-arg owner action / bare signal). */
    public void emit() {
        sink.accept(null);
    }

    /** Wire the sink. Package-private so only {@link OutputBinder} can bind it; a null sink is a no-op. */
    void bind(Consumer<Object> sink) {
        this.sink = (sink == null) ? payload -> {} : sink;
    }
}
