package app.besoft.medley.spring;

import app.besoft.medley.core.component.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * Discovers {@code @MedleyRoute} beans and maps their URL path to the component class.
 *
 * <p>Components are resolved through the Spring context as prototype beans, so each page load
 * gets a fresh instance with full dependency injection (repositories, services, security).
 * Scanning works off bean <em>definitions</em> (types + scope metadata) so prototype beans are
 * not eagerly instantiated here.</p>
 */
public class RouteRegistry {

    private final ConfigurableListableBeanFactory beanFactory;
    private final Map<String, Class<? extends Component>> routes = new ConcurrentHashMap<>();

    public RouteRegistry(ConfigurableListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
        scan();
    }

    private void scan() {
        for (String name : beanFactory.getBeanNamesForAnnotation(MedleyRoute.class)) {
            Class<?> cls = beanFactory.getType(name);
            if (cls == null) continue;
            MedleyRoute route = beanFactory.findAnnotationOnBean(name, MedleyRoute.class);
            if (route == null) continue;
            if (!Component.class.isAssignableFrom(cls)) {
                throw new IllegalStateException(cls.getName()
                        + " has @MedleyRoute but does not extend " + Component.class.getName());
            }
            // No backing definition => a directly registered singleton instance, which is exactly
            // the shared-state case we must reject; otherwise check the definition's scope.
            boolean singleton = !beanFactory.containsBeanDefinition(name)
                    || beanFactory.getBeanDefinition(name).isSingleton();
            if (singleton) {
                throw new IllegalStateException("@MedleyRoute component " + cls.getName()
                        + " must not be singleton — each session needs its own instance so state is"
                        + " not shared across users. @MedleyRoute already meta-annotates prototype"
                        + " scope; remove any @Scope override that reverts it to singleton.");
            }
            @SuppressWarnings("unchecked")
            Class<? extends Component> compClass = (Class<? extends Component>) cls;
            routes.put(route.value(), compClass);
        }
    }

    public Class<? extends Component> resolve(String path) {
        return routes.get(path);
    }

    /** Create a fresh component bean for the given class (prototype scope, so a new instance). */
    public Component newComponent(Class<? extends Component> cls) {
        return beanFactory.getBean(cls);
    }

    public Map<String, Class<? extends Component>> routes() {
        return Map.copyOf(routes);
    }
}
