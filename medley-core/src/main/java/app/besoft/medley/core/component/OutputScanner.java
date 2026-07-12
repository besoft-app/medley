package app.besoft.medley.core.component;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds the {@code @Output} lookup table for a component class — the child→parent callback channels
 * a parent may bind on a {@code <medley-component>} boundary. Mirrors {@link ParamScanner}: only
 * fields marked {@code @Output} are wireable outputs; anything else is off-limits. Type validation
 * (the field must be an {@link EventEmitter}) is left to {@link OutputBinder}.
 */
final class OutputScanner {

    private OutputScanner() {}

    static Map<String, Field> scan(Class<?> cls) {
        Map<String, Field> out = new HashMap<>();
        Class<?> c = cls;
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(Annotations.Output.class)) {
                    out.putIfAbsent(f.getName(), f);
                }
            }
            c = c.getSuperclass();
        }
        return out;
    }
}
