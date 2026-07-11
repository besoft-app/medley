package app.besoft.medley.core.component;

import app.besoft.medley.core.template.TemplateException;

/**
 * Lenient coercion of a client- or template-supplied scalar to a declared target type.
 *
 * <p>Shared by {@code @Action} argument binding ({@link ComponentInstance}) and {@code @Param}
 * injection ({@link ParamBinder}). Handles String, the boxed/primitive numeric types and boolean.
 * A value that cannot be parsed to the target throws a {@link TemplateException} (a handled failure
 * that keeps the socket open), rather than surfacing a raw reflection error.</p>
 */
public final class Coercions {

    private Coercions() {}

    public static Object coerce(Object value, Class<?> target) {
        if (value == null || target.isInstance(value)) {
            return value;
        }
        if (target == String.class) {
            return value.toString();
        }
        if (value instanceof Number n) {
            if (target == int.class || target == Integer.class) return n.intValue();
            if (target == long.class || target == Long.class) return n.longValue();
            if (target == double.class || target == Double.class) return n.doubleValue();
            if (target == float.class || target == Float.class) return n.floatValue();
            if (target == short.class || target == Short.class) return n.shortValue();
            if (target == byte.class || target == Byte.class) return n.byteValue();
        }
        if (value instanceof Boolean b && (target == boolean.class || target == Boolean.class)) {
            return b;
        }
        String s = value.toString().trim();
        try {
            if (target == int.class || target == Integer.class) return Integer.valueOf(s);
            if (target == long.class || target == Long.class) return Long.valueOf(s);
            if (target == double.class || target == Double.class) return Double.valueOf(s);
            if (target == float.class || target == Float.class) return Float.valueOf(s);
            if (target == short.class || target == Short.class) return Short.valueOf(s);
            if (target == byte.class || target == Byte.class) return Byte.valueOf(s);
            if (target == boolean.class || target == Boolean.class) return Boolean.valueOf(s);
        } catch (NumberFormatException e) {
            throw new TemplateException("Cannot coerce '" + s + "' to " + target.getSimpleName());
        }
        // Unknown target type — pass the raw value through and let the caller fail if incompatible.
        return value;
    }
}
