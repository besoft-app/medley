package app.besoft.medley.core.component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds the action lookup table for a component class.
 *
 * <p>Only methods annotated with {@code @Action} are callable from the client. This is the
 * server-side whitelist referenced in the security model: the client sends an action name,
 * the server looks it up here, and anything not present is rejected. Method names are never
 * reflected blindly from client input.</p>
 */
final class ActionScanner {

    private ActionScanner() {}

    static Map<String, Method> scan(Class<?> cls) {
        Map<String, Method> out = new HashMap<>();
        Class<?> c = cls;
        while (c != null && c != Object.class) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.isAnnotationPresent(Annotations.Action.class)) {
                    out.putIfAbsent(m.getName(), m);
                }
            }
            c = c.getSuperclass();
        }
        return out;
    }
}
