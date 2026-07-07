package app.besoft.medley.spring;

import app.besoft.medley.core.component.Component;
import app.besoft.medley.core.component.ComponentInstance;
import app.besoft.medley.core.template.TemplateRenderer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the live component instances for a single user session.
 *
 * <p>In the PoC each top-level route renders one root component, registered here under its
 * generated id. Events arriving over the WebSocket are dispatched to the matching instance.
 * The session is the unit that bounds server-side state: when it ends, its component tree
 * (and all {@code @State}) is released.</p>
 *
 * <p>Concurrency: events for one session are processed one at a time (see the WebSocket
 * handler), so a {@code ConcurrentHashMap} for the registry plus single-threaded render
 * loop per session keeps diffs deterministic.</p>
 */
public class MedleySession {

    private final TemplateRegistry templates;
    private final Map<String, ComponentInstance> instances = new ConcurrentHashMap<>();

    public MedleySession(TemplateRegistry templates) {
        this.templates = templates;
    }

    /** Mount a freshly created component bean under a generated id; returns the instance. */
    public ComponentInstance mount(String id, Component component) {
        TemplateRenderer renderer = templates.rendererFor(component.getClass());
        ComponentInstance instance = new ComponentInstance(id, component, renderer);
        instances.put(id, instance);
        return instance;
    }

    public ComponentInstance get(String id) {
        return instances.get(id);
    }

    public boolean contains(String id) {
        return instances.containsKey(id);
    }

    public void remove(String id) {
        ComponentInstance removed = instances.remove(id);
        if (removed != null) {
            removed.component().onDestroy();
        }
    }
}
