package app.besoft.medley.core.template;

/**
 * Resolves a named partial (a reusable template fragment) to its parsed root element.
 *
 * <p>Partials are the composition mechanism for the component library (Stage 4, increment 4): a
 * {@code <medley-partial name="field" .../>} in an owner template is expanded <em>inline</em> into
 * the owner's VNode tree and id-space, evaluated against a local scope built from the passed
 * attributes. There is no separate server component, no child state, and no wire change.</p>
 *
 * <p>Core stays free of file/classpath IO: the engine only asks "give me the fragment named X".
 * The starter supplies an implementation that loads and caches fragment templates from resources.</p>
 */
@FunctionalInterface
public interface PartialResolver {

    /**
     * @param name the {@code name} attribute of a {@code <medley-partial>}
     * @return the parsed root element of that fragment, or {@code null} if no such partial exists
     */
    TemplateNode.Element resolve(String name);
}
