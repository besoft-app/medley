package app.besoft.medley.core.template;

/**
 * Per-render state threaded through {@link TemplateRenderer}'s recursion. A renderer is parsed once and
 * shared across every session, so nothing render-specific may live on it; this carries what the
 * recursion needs instead of growing the parameter list of every private method.
 *
 * <p>{@code slotsExpanded} is shared with derived (deeper) contexts on purpose: a
 * {@code <medley-slot>} reached through a partial must count against the same render, because
 * expanding a slot's bucket twice would put duplicate ids in the DOM (MEDLEY_DESIGN §11a).</p>
 */
final class RenderContext {

    private final String componentId;
    private final int depth;
    private final ComponentHost host;
    private final Projection projection;
    private final java.util.Set<String> slotsExpanded;

    RenderContext(String componentId, int depth, ComponentHost host, Projection projection) {
        this(componentId, depth, host, projection, new java.util.HashSet<>());
    }

    private RenderContext(String componentId, int depth, ComponentHost host, Projection projection,
                          java.util.Set<String> slotsExpanded) {
        this.componentId = componentId;
        this.depth = depth;
        this.host = host;
        this.projection = projection == null ? Projection.EMPTY : projection;
        this.slotsExpanded = slotsExpanded;
    }

    String componentId() { return componentId; }
    int depth() { return depth; }
    ComponentHost host() { return host; }
    Projection projection() { return projection; }

    /** One level deeper (partial / component expansion), sharing this render's expanded-slot set. */
    RenderContext deeper() {
        return new RenderContext(componentId, depth + 1, host, projection, slotsExpanded);
    }

    /** Register a slot expansion by name; false if this name was already expanded this render (a repeat
     *  would splice the same bucket twice → duplicate ids). Shared across derived contexts. */
    boolean claimSlot(String slotName) {
        return slotsExpanded.add(slotName);
    }
}
