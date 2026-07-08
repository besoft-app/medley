package app.besoft.medley.spring;

import app.besoft.medley.core.component.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * Discovers {@code @MedleyIsland} handlers and dispatches {@code @IslandAction} commits to them.
 *
 * <p>Mirrors {@link RouteRegistry}: it scans bean definitions (no eager surprises) and caches the
 * action lookup per island. Handlers are stateless singletons; a commit mutates the owning
 * {@link Component}, whose subsequent re-render produces the patches (including any props-push
 * back to the island as a host-attribute patch).</p>
 */
public class IslandRegistry {

    private final ConfigurableListableBeanFactory beanFactory;
    private final ObjectMapper mapper;
    private final Map<String, Handler> islands = new ConcurrentHashMap<>();

    private record Handler(Object bean, Map<String, Method> actions) {}

    public IslandRegistry(ConfigurableListableBeanFactory beanFactory, ObjectMapper mapper) {
        this.beanFactory = beanFactory;
        this.mapper = mapper;
        scan();
    }

    private void scan() {
        for (String name : beanFactory.getBeanNamesForAnnotation(MedleyIsland.class)) {
            Class<?> cls = beanFactory.getType(name);
            if (cls == null) continue;
            MedleyIsland ann = beanFactory.findAnnotationOnBean(name, MedleyIsland.class);
            if (ann == null) continue;

            Map<String, Method> actions = new HashMap<>();
            for (Method m : cls.getDeclaredMethods()) {
                IslandAction a = m.getAnnotation(IslandAction.class);
                if (a == null) continue;
                m.setAccessible(true);
                actions.put(a.value().isEmpty() ? m.getName() : a.value(), m);
            }
            islands.put(ann.value(), new Handler(beanFactory.getBean(name), actions));
        }
    }

    public boolean contains(String island) {
        return islands.containsKey(island);
    }

    /** Invoke {@code island.action} against the owning component with the committed payload. */
    public void invoke(String island, String action, Component owner, JsonNode payload) {
        Handler handler = islands.get(island);
        if (handler == null) {
            throw new IllegalArgumentException("No @MedleyIsland named '" + island + "'");
        }
        Method method = handler.actions().get(action);
        if (method == null) {
            throw new IllegalArgumentException(
                    "No @IslandAction '" + action + "' on island '" + island + "'");
        }
        try {
            method.invoke(handler.bean(), bindArgs(method, owner, payload));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Island action '" + island + "." + action + "' failed", e);
        }
    }

    private Object[] bindArgs(Method method, Component owner, JsonNode payload) {
        Parameter[] params = method.getParameters();
        long nonOwner = Arrays.stream(params)
                .filter(p -> !Component.class.isAssignableFrom(p.getType()))
                .count();

        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            Parameter p = params[i];
            if (Component.class.isAssignableFrom(p.getType())) {
                args[i] = owner;
            } else if (payload == null) {
                args[i] = null;
            } else {
                JsonNode field = payload.get(p.getName());
                // A single non-owner param binds the whole payload object (DTO); otherwise bind
                // the matching payload field by parameter name (requires -parameters).
                JsonNode source = (field == null && nonOwner == 1) ? payload : field;
                args[i] = source == null ? null : mapper.convertValue(source, p.getType());
            }
        }
        return args;
    }
}
