package app.besoft.medley.core.component;

import app.besoft.medley.core.template.TemplateException;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Injects parent-supplied values into a child component's {@code @Param} fields (Stage 4, increment
 * 4b), coercing each to the field's declared type via {@link Coercions}. Only {@code @Param} fields
 * are written; a passed attribute with no matching {@code @Param} is ignored (it stays a host
 * attribute), and a {@code @Param} the parent did not pass keeps its default.
 */
public final class ParamBinder {

    private ParamBinder() {}

    public static void inject(Object component, Map<String, Object> params) {
        Map<String, Field> fields = ParamScanner.scan(component.getClass());
        for (Map.Entry<String, Field> e : fields.entrySet()) {
            Object value = params.get(e.getKey());
            if (value == null && !params.containsKey(e.getKey())) {
                continue; // not passed — keep the field's default
            }
            Field f = e.getValue();
            try {
                f.setAccessible(true);
                f.set(component, Coercions.coerce(value, f.getType()));
            } catch (IllegalAccessException | IllegalArgumentException ex) {
                // IllegalArgumentException covers null (or otherwise unassignable) into a primitive
                // @Param — surface it as a handled failure rather than a raw reflection error. A
                // coercion failure from Coercions is already a TemplateException and flows through.
                throw new TemplateException("Failed to inject @Param '" + e.getKey() + "' on "
                        + component.getClass().getSimpleName(), ex);
            }
        }
    }
}
