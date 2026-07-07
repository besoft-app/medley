package app.besoft.medley.core.template;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hand-written, single-pass parser for Medley templates.
 *
 * <p>Templates are an HTML subset with four special attribute prefixes:
 * <pre>
 *   {{ expr }}       text interpolation (HTML-escaped at render time)
 *   :attr="expr"     bound attribute (expression evaluated per render)
 *   @event="action"  bind DOM event to a server @Action
 *   *if="expr"       conditional rendering of this element + subtree
 *   *for="x : items" repeat element for each item; pair with key="..."
 *   key="expr"       stable key for list reconciliation by the differ
 * </pre>
 *
 * <p>Why hand-written rather than reusing an HTML library or Thymeleaf: we need full
 * control over the produced AST so that rendering can attach stable {@code medley-id}s
 * and so the structural directives map cleanly onto VNode generation. It is also small
 * enough to read in one sitting — a stated design goal.</p>
 *
 * <p>Deliberate limitations of this PoC parser: void/self-closing elements must be
 * written as {@code <br/>}; attribute values must be double-quoted; no HTML comments
 * inside elements; no CDATA. These are easy to extend later.</p>
 */
public final class TemplateParser {

    /** HTML void elements that never have a closing tag. */
    private static final Set<String> VOID_ELEMENTS = Set.of(
            "area", "base", "br", "col", "embed", "hr", "img", "input",
            "link", "meta", "param", "source", "track", "wbr"
    );

    private final String src;
    private int pos;

    private TemplateParser(String src) {
        this.src = src;
    }

    /** Parse a template string into a single root element. */
    public static TemplateNode.Element parse(String template) {
        TemplateParser p = new TemplateParser(template.strip());
        p.skipWhitespaceAndComments();
        TemplateNode node = p.parseNode();
        if (!(node instanceof TemplateNode.Element el)) {
            throw new TemplateException("Template root must be a single element");
        }
        p.skipWhitespaceAndComments();
        if (p.pos < p.src.length()) {
            throw new TemplateException("Template must have a single root element; trailing content at " + p.pos);
        }
        return el;
    }

    // --- node-level parsing ---

    private List<TemplateNode> parseChildren() {
        List<TemplateNode> children = new ArrayList<>();
        for (;;) {
            if (pos >= src.length()) break;
            if (lookingAtCloseTag()) break;
            TemplateNode child = parseNode();
            if (child != null) children.add(child);
        }
        return children;
    }

    private TemplateNode parseNode() {
        if (src.startsWith("<!--", pos)) { skipComment(); return null; }
        if (peek() == '<') return parseElement();
        return parseTextOrInterpolation();
    }

    private TemplateNode.Element parseElement() {
        expect('<');
        String tag = readName();
        if (tag.isEmpty()) throw new TemplateException("Expected tag name at pos " + pos);

        Map<String, String> staticAttrs = new LinkedHashMap<>();
        Map<String, String> boundAttrs = new LinkedHashMap<>();
        Map<String, String> events = new LinkedHashMap<>();
        String ifExpr = null, forVar = null, forExpr = null, keyExpr = null;

        for (;;) {
            skipWhitespace();
            char c = peek();
            if (c == '>' || c == '/') break;

            String attrName = readAttributeName();
            String value = "";
            skipWhitespace();
            if (peek() == '=') {
                next(); // consume '='
                skipWhitespace();
                value = readQuotedValue();
            }

            if (attrName.startsWith(":")) {
                boundAttrs.put(attrName.substring(1), value);
            } else if (attrName.startsWith("@")) {
                events.put(attrName.substring(1), value);
            } else if (attrName.equals("*if")) {
                ifExpr = value;
            } else if (attrName.equals("*for")) {
                String[] parts = value.split(":", 2);
                if (parts.length != 2) {
                    throw new TemplateException("*for must be 'var : iterableExpr', got: " + value);
                }
                forVar = parts[0].strip();
                forExpr = parts[1].strip();
            } else if (attrName.equals("key")) {
                keyExpr = value;
            } else {
                staticAttrs.put(attrName, value);
            }
        }

        boolean selfClosed = false;
        if (peek() == '/') { next(); selfClosed = true; }
        expect('>');

        List<TemplateNode> children;
        if (selfClosed || VOID_ELEMENTS.contains(tag)) {
            children = List.of();
        } else {
            children = parseChildren();
            parseCloseTag(tag);
        }

        return new TemplateNode.Element(
                tag, staticAttrs, boundAttrs, events,
                ifExpr, forVar, forExpr, keyExpr, children
        );
    }

    private TemplateNode parseTextOrInterpolation() {
        // an interpolation that starts exactly here
        if (src.startsWith("{{", pos)) {
            pos += 2;
            int end = src.indexOf("}}", pos);
            if (end < 0) throw new TemplateException("Unterminated interpolation at pos " + pos);
            String expr = src.substring(pos, end).strip();
            pos = end + 2;
            return new TemplateNode.Interpolation(expr);
        }
        // otherwise: literal text up to next '<' or '{{'
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            if (peek() == '<') break;
            if (src.startsWith("{{", pos)) break;
            sb.append(next());
        }
        return new TemplateNode.Text(sb.toString());
    }

    // --- tag helpers ---

    private boolean lookingAtCloseTag() {
        return src.startsWith("</", pos);
    }

    private void parseCloseTag(String expectedTag) {
        skipWhitespaceAndComments();
        if (!src.startsWith("</", pos)) {
            throw new TemplateException("Expected </" + expectedTag + "> but found end of input");
        }
        pos += 2;
        String tag = readName();
        if (!tag.equals(expectedTag)) {
            throw new TemplateException("Mismatched closing tag: expected </" + expectedTag + "> but got </" + tag + ">");
        }
        skipWhitespace();
        expect('>');
    }

    // --- lexical helpers ---

    private String readName() {
        int start = pos;
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_') pos++;
            else break;
        }
        return src.substring(start, pos);
    }

    private String readAttributeName() {
        int start = pos;
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (Character.isWhitespace(c) || c == '=' || c == '>' || c == '/') break;
            pos++;
        }
        return src.substring(start, pos);
    }

    private String readQuotedValue() {
        char quote = peek();
        if (quote != '"' && quote != '\'') {
            throw new TemplateException("Attribute value must be quoted at pos " + pos);
        }
        next(); // opening quote
        int start = pos;
        while (pos < src.length() && src.charAt(pos) != quote) pos++;
        if (pos >= src.length()) throw new TemplateException("Unterminated attribute value at pos " + start);
        String value = src.substring(start, pos);
        next(); // closing quote
        return value;
    }

    private void skipWhitespace() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
    }

    private void skipComment() {
        int end = src.indexOf("-->", pos);
        if (end < 0) throw new TemplateException("Unterminated comment at pos " + pos);
        pos = end + 3;
    }

    private void skipWhitespaceAndComments() {
        for (;;) {
            skipWhitespace();
            if (src.startsWith("<!--", pos)) skipComment();
            else break;
        }
    }

    private char peek() {
        return pos < src.length() ? src.charAt(pos) : '\0';
    }

    private char next() {
        return src.charAt(pos++);
    }

    private void expect(char c) {
        if (peek() != c) {
            throw new TemplateException("Expected '" + c + "' at pos " + pos + " but found '" + peek() + "'");
        }
        next();
    }
}
