package app.besoft.medley.core.component;

import app.besoft.medley.core.template.TemplateException;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Wires a child component's {@code @Output EventEmitter} fields to sinks supplied by the host, giving
 * the child a channel to call back up to its parent. Mirrors {@link ParamBinder}, but for the
 * event-up direction.
 *
 * <p>For <em>every</em> declared {@code @Output} field an {@link EventEmitter} is ensured (created if
 * the field is null), so the child can safely {@code emit()} even when the parent bound nothing —
 * that emitter's sink stays a no-op. Where the parent <em>did</em> bind the output (a {@code sinks}
 * entry keyed by the field name), the emitter's sink is bound to invoke the owner action.</p>
 *
 * <p>Unlike {@code @Param}, an output binding is static — it is the template position of the
 * {@code <medley-component>}, not a per-render value — so this runs at mount, not on every parent
 * re-render. It may be called twice at mount: once with an empty map to ensure emitters exist before
 * {@code onInit} (so emitting there is a safe no-op), then once to bind the real owner-action sinks.</p>
 */
public final class OutputBinder {

    private OutputBinder() {}

    public static void inject(Object component, Map<String, Consumer<Object>> sinks) {
        Map<String, Field> fields = OutputScanner.scan(component.getClass());
        for (Map.Entry<String, Field> e : fields.entrySet()) {
            Field f = e.getValue();
            if (f.getType() != EventEmitter.class) {
                throw new TemplateException("@Output field '" + e.getKey() + "' on "
                        + component.getClass().getSimpleName() + " must be of type EventEmitter");
            }
            try {
                f.setAccessible(true);
                EventEmitter emitter = (EventEmitter) f.get(component);
                if (emitter == null) {
                    emitter = new EventEmitter();
                    f.set(component, emitter);
                }
                emitter.bind(sinks.get(e.getKey())); // null sink -> no-op (unbound output)
            } catch (IllegalAccessException ex) {
                throw new TemplateException("Failed to wire @Output '" + e.getKey() + "' on "
                        + component.getClass().getSimpleName(), ex);
            }
        }
    }
}
