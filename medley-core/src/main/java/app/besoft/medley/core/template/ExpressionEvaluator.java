package app.besoft.medley.core.template;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Evaluates the deliberately small expression language used inside templates.
 *
 * <p>Supported, against a single "context" object (the component instance):
 * <ul>
 *   <li>field / zero-arg getter access: {@code count}, {@code label}, {@code user.name}</li>
 *   <li>Map value by key in a dotted path: {@code errors.name} (absent key → {@code null})</li>
 *   <li>literals: integers, {@code true}/{@code false}, single-quoted strings</li>
 *   <li>comparisons: {@code == != < <= > >=}</li>
 *   <li>boolean ops: {@code && || !}</li>
 * </ul>
 *
 * <p>Deliberately NOT supported: method calls with arguments, arithmetic beyond
 * comparison, static access, assignment. Logic belongs in Java, not the template.
 * This keeps the engine simple and removes a large class of injection/abuse risks.</p>
 */
public final class ExpressionEvaluator {

    private final Object context;

    public ExpressionEvaluator(Object context) {
        this.context = context;
    }

    public Object eval(String expr) {
        return new Parser(expr).parseExpression();
    }

    public boolean evalBoolean(String expr) {
        return truthy(eval(expr));
    }

    public String evalString(String expr) {
        Object v = eval(expr);
        return v == null ? "" : String.valueOf(v);
    }

    static boolean truthy(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.doubleValue() != 0;
        if (v instanceof String s) return !s.isEmpty();
        if (v instanceof List<?> l) return !l.isEmpty();
        return true;
    }

    /** Recursive-descent parser. Grammar (loosest to tightest binding):
     *  or := and ('||' and)*
     *  and := equality ('&&' equality)*
     *  equality := comparison (('=='|'!=') comparison)*
     *  comparison := unary (('<'|'<='|'>'|'>=') unary)*
     *  unary := '!' unary | primary
     *  primary := literal | path | '(' or ')'
     */
    private final class Parser {
        private final String s;
        private int pos;

        Parser(String s) { this.s = s; }

        Object parseExpression() {
            Object v = parseOr();
            skipWs();
            if (pos < s.length()) {
                throw new TemplateException("Unexpected trailing input in expression: '" + s + "'");
            }
            return v;
        }

        private Object parseOr() {
            Object left = parseAnd();
            while (match("||")) {
                Object right = parseAnd();
                left = truthy(left) || truthy(right);
            }
            return left;
        }

        private Object parseAnd() {
            Object left = parseEquality();
            while (match("&&")) {
                Object right = parseEquality();
                left = truthy(left) && truthy(right);
            }
            return left;
        }

        private Object parseEquality() {
            Object left = parseComparison();
            for (;;) {
                if (match("==")) left = equalsLoose(left, parseComparison());
                else if (match("!=")) left = !equalsLoose(left, parseComparison());
                else return left;
            }
        }

        private Object parseComparison() {
            Object left = parseUnary();
            for (;;) {
                if (match("<=")) left = compare(left, parseUnary()) <= 0;
                else if (match(">=")) left = compare(left, parseUnary()) >= 0;
                else if (match("<")) left = compare(left, parseUnary()) < 0;
                else if (match(">")) left = compare(left, parseUnary()) > 0;
                else return left;
            }
        }

        private Object parseUnary() {
            if (match("!")) return !truthy(parseUnary());
            return parsePrimary();
        }

        private Object parsePrimary() {
            skipWs();
            if (match("(")) {
                Object v = parseOr();
                expect(")");
                return v;
            }
            char c = peek();
            if (c == '\'') return parseStringLiteral();
            if (Character.isDigit(c)) return parseNumber();
            if (startsWith("true")) { pos += 4; return Boolean.TRUE; }
            if (startsWith("false")) { pos += 5; return Boolean.FALSE; }
            if (startsWith("null")) { pos += 4; return null; }
            return parsePath();
        }

        private Object parseStringLiteral() {
            expect("'");
            StringBuilder sb = new StringBuilder();
            while (pos < s.length() && s.charAt(pos) != '\'') sb.append(s.charAt(pos++));
            expect("'");
            return sb.toString();
        }

        private Object parseNumber() {
            int start = pos;
            while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            return Integer.parseInt(s.substring(start, pos));
        }

        private Object parsePath() {
            skipWs();
            int start = pos;
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (Character.isLetterOrDigit(c) || c == '_' || c == '.') pos++;
                else break;
            }
            String path = s.substring(start, pos).trim();
            if (path.isEmpty()) throw new TemplateException("Empty expression near pos " + pos + " in '" + s + "'");
            return resolvePath(path);
        }

        private Object resolvePath(String path) {
            Object current = context;
            for (String segment : path.split("\\.")) {
                if (current == null) return null;
                current = readMember(current, segment);
            }
            return current;
        }

        // --- low-level helpers ---
        private void skipWs() { while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++; }
        private char peek() { skipWs(); return pos < s.length() ? s.charAt(pos) : '\0'; }
        private boolean startsWith(String t) { skipWs(); return s.startsWith(t, pos); }

        private boolean match(String t) {
            skipWs();
            if (s.startsWith(t, pos)) { pos += t.length(); return true; }
            return false;
        }

        private void expect(String t) {
            if (!match(t)) throw new TemplateException("Expected '" + t + "' in expression '" + s + "'");
        }
    }

    /** Reads a field, zero-arg getter, or (on a Map) a value by key. */
    private static Object readMember(Object target, String name) {
        // Loop scopes resolve the loop variable locally, then delegate to the parent context.
        if (target instanceof TemplateRenderer.ScopedContext scope) {
            if (scope.locals().containsKey(name)) {
                return scope.locals().get(name);
            }
            return readMember(scope.parent(), name);
        }
        // On a Map, dotted access is a key lookup (the error-bag pattern: {{ errors.name }}). This
        // precedes getter/field resolution so a key never collides with Map methods (isEmpty/size);
        // an absent key returns null, which renders as a hidden *if / empty interpolation.
        if (target instanceof Map<?, ?> map) {
            return map.get(name);
        }
        Class<?> cls = target.getClass();
        // try getter: getX() / isX()
        String cap = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (String prefix : new String[]{"get", "is"}) {
            try {
                Method m = cls.getMethod(prefix + cap);
                m.setAccessible(true);
                return m.invoke(target);
            } catch (NoSuchMethodException ignored) {
                // fall through
            } catch (Exception e) {
                throw new TemplateException("Failed reading '" + name + "' on " + cls.getSimpleName(), e);
            }
        }
        // try field (including non-public, walking up the hierarchy)
        Class<?> c = cls;
        while (c != null && c != Object.class) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            } catch (Exception e) {
                throw new TemplateException("Failed reading field '" + name + "' on " + cls.getSimpleName(), e);
            }
        }
        throw new TemplateException("No field or getter '" + name + "' on " + cls.getSimpleName());
    }

    private static boolean equalsLoose(Object a, Object b) {
        if (a == null || b == null) return a == b;
        if (a instanceof Number na && b instanceof Number nb) {
            return na.doubleValue() == nb.doubleValue();
        }
        return a.equals(b);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compare(Object a, Object b) {
        if (a instanceof Number na && b instanceof Number nb) {
            return Double.compare(na.doubleValue(), nb.doubleValue());
        }
        if (a instanceof Comparable ca && b != null) {
            return ca.compareTo(b);
        }
        throw new TemplateException("Cannot compare " + a + " and " + b);
    }
}
