package app.besoft.medley.core.template;

import java.util.Map;

/**
 * Creates a child component instance for a {@code <medley-component name="x" ...>} boundary
 * (Stage 4, increment 4b). The renderer resolves the passed attributes to param values (static →
 * string, {@code :attr} → evaluated against the owner) and asks the factory for a ready-to-render
 * child: a fresh component with its {@code @Param} fields injected and its lifecycle started, plus
 * the {@link TemplateRenderer} for that child's template.
 *
 * <p>Core stays free of Spring / bean creation: the starter implements this over its component
 * registry (fresh prototype bean) + template registry, mirroring {@link PartialResolver}.</p>
 */
@FunctionalInterface
public interface ChildComponentFactory {

    /**
     * @param name   the {@code name} attribute of the {@code <medley-component>}
     * @param params resolved param values by attribute name (owner-evaluated for {@code :attr})
     * @return the child ready to render, or {@code null} if no component is registered under {@code name}
     */
    Child create(String name, Map<String, Object> params);

    /** A child component paired with the renderer for its template. */
    record Child(Object component, TemplateRenderer renderer) {}
}
