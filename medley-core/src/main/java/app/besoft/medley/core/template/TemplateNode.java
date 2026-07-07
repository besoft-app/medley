package app.besoft.medley.core.template;

import java.util.List;
import java.util.Map;

/**
 * Parsed, reusable representation of a template. Parsing happens once per template file;
 * rendering walks this AST against component state to produce a VNode tree.
 *
 * <p>This separation matters for performance: the (relatively expensive) string parsing
 * is done a single time, while the per-event render only does cheap AST traversal +
 * expression evaluation.</p>
 */
public sealed interface TemplateNode permits TemplateNode.Element, TemplateNode.Text, TemplateNode.Interpolation {

    /**
     * An element. Attribute handling distinguishes four kinds, decided at parse time:
     * <ul>
     *   <li>static attribute: {@code class="counter"}</li>
     *   <li>bound attribute {@code :attr="expr"} -> evaluated each render</li>
     *   <li>event {@code @event="action"} -> wired to a server action</li>
     *   <li>structural directives {@code *if} / {@code *for} -> control flow</li>
     * </ul>
     *
     * @param tag           lowercase tag name
     * @param staticAttrs   literal attributes
     * @param boundAttrs    attribute name -> expression (evaluated per render)
     * @param events        DOM event -> action name
     * @param ifExpr        expression for *if, or null
     * @param forVar        loop variable name for *for, or null
     * @param forExpr       iterable expression for *for, or null
     * @param keyExpr       expression for the stable key (used by the differ), or null
     * @param children      child nodes
     */
    record Element(
            String tag,
            Map<String, String> staticAttrs,
            Map<String, String> boundAttrs,
            Map<String, String> events,
            String ifExpr,
            String forVar,
            String forExpr,
            String keyExpr,
            List<TemplateNode> children
    ) implements TemplateNode {}

    /** Literal text run. */
    record Text(String value) implements TemplateNode {}

    /** A {@code {{ expr }}} interpolation. */
    record Interpolation(String expr) implements TemplateNode {}
}
