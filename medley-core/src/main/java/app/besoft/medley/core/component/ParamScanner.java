package app.besoft.medley.core.component;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds the {@code @Param} lookup table for a component class — the inputs a parent may pass down
 * to a child component (Stage 4, increment 4b). Mirrors {@link ActionScanner}: only fields marked
 * {@code @Param} are injectable from a parent's template; anything else is off-limits.
 */
final class ParamScanner {

    private ParamScanner() {}

    static Map<String, Field> scan(Class<?> cls) {
        Map<String, Field> out = new HashMap<>();
        Class<?> c = cls;
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(Annotations.Param.class)) {
                    out.putIfAbsent(f.getName(), f);
                }
            }
            c = c.getSuperclass();
        }
        return out;
    }
}
