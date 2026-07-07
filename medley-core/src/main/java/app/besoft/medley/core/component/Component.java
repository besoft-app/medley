package app.besoft.medley.core.component;

/**
 * Base class for Medley components.
 *
 * <p>Subclasses declare {@code @State} fields, {@code @Param} inputs and {@code @Action}
 * methods, plus a template. The lifecycle hooks are optional overrides.</p>
 */
public abstract class Component {

    private String componentId;

    /** Assigned by the runtime when the component is mounted into the tree. */
    public final void bindId(String id) {
        this.componentId = id;
    }

    public final String componentId() {
        return componentId;
    }

    /** Called once after construction and parameter injection. */
    public void onInit() {}

    /** Called when a parent updates this component's {@code @Param} inputs. */
    public void onParamChange() {}

    /** Called before the component is removed from the tree. */
    public void onDestroy() {}
}
