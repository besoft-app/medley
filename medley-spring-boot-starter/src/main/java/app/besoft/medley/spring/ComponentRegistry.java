package app.besoft.medley.spring;

import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * Discovers {@code @MedleyChild} beans and maps their {@code @MedleyComponent} name to the class,
 * so {@code <medley-component name="x">} can mount a fresh child instance (Stage 4, increment 4b).
 *
 * <p>Mirrors {@link RouteRegistry}: children are prototype beans (a new instance per placement, with
 * DI), and scanning fails fast if a child is singleton — that would share {@code @State} across
 * placements. Scanning works off bean <em>definitions</em> so prototypes are not eagerly created.</p>
 */
public class ComponentRegistry {

    private final ConfigurableListableBeanFactory beanFactory;
    private final Map<String, Class<? extends Component>> byName = new ConcurrentHashMap<>();

    public ComponentRegistry(ConfigurableListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
        scan();
    }

    private void scan() {
        for (String beanName : beanFactory.getBeanNamesForAnnotation(MedleyChild.class)) {
            Class<?> cls = beanFactory.getType(beanName);
            if (cls == null) continue;
            if (!Component.class.isAssignableFrom(cls)) {
                throw new IllegalStateException(cls.getName()
                        + " has @MedleyChild but does not extend " + Component.class.getName());
            }
            MedleyComponent mc = cls.getAnnotation(MedleyComponent.class);
            if (mc == null) {
                throw new IllegalStateException(cls.getName()
                        + " has @MedleyChild but is not @MedleyComponent");
            }
            boolean singleton = !beanFactory.containsBeanDefinition(beanName)
                    || beanFactory.getBeanDefinition(beanName).isSingleton();
            if (singleton) {
                throw new IllegalStateException("@MedleyChild component " + cls.getName()
                        + " must not be singleton — each placement needs its own instance so @State"
                        + " is not shared. @MedleyChild already meta-annotates prototype scope;"
                        + " remove any @Scope override that reverts it to singleton.");
            }
            @SuppressWarnings("unchecked")
            Class<? extends Component> compClass = (Class<? extends Component>) cls;
            byName.put(mc.value(), compClass);
        }
    }

    /** Create a fresh child instance for the given name, or null if no such {@code @MedleyChild}. */
    public Component newInstance(String name) {
        Class<? extends Component> cls = byName.get(name);
        return cls == null ? null : beanFactory.getBean(cls);
    }
}
