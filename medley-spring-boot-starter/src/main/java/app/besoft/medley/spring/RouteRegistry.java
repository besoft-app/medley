package app.besoft.medley.spring;

import app.besoft.medley.core.component.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.ApplicationContext;

/**
 * Discovers {@code @MedleyRoute} beans and maps their URL path to the component class.
 *
 * <p>Components are resolved through the Spring context as prototype beans, so each page load
 * gets a fresh instance with full dependency injection (repositories, services, security).</p>
 */
public class RouteRegistry {

    private final ApplicationContext context;
    private final Map<String, Class<? extends Component>> routes = new ConcurrentHashMap<>();

    public RouteRegistry(ApplicationContext context) {
        this.context = context;
        scan();
    }

    private void scan() {
        Map<String, Object> beans = context.getBeansWithAnnotation(MedleyRoute.class);
        for (Object bean : beans.values()) {
            Class<?> cls = bean.getClass();
            MedleyRoute route = cls.getAnnotation(MedleyRoute.class);
            if (route == null) continue;
            if (!Component.class.isAssignableFrom(cls)) {
                throw new IllegalStateException(cls.getName() + " has @MedleyRoute but does not extend Component");
            }
            @SuppressWarnings("unchecked")
            Class<? extends Component> compClass = (Class<? extends Component>) cls;
            routes.put(route.value(), compClass);
        }
    }

    public Class<? extends Component> resolve(String path) {
        return routes.get(path);
    }

    /** Create a fresh component bean for the given class (prototype scope expected). */
    public Component newComponent(Class<? extends Component> cls) {
        return context.getBean(cls);
    }

    public Map<String, Class<? extends Component>> routes() {
        return Map.copyOf(routes);
    }
}
