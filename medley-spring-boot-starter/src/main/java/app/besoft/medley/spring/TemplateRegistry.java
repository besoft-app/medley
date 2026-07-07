package app.besoft.medley.spring;

import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.template.TemplateException;
import app.besoft.medley.core.template.TemplateRenderer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.io.ClassPathResource;

/**
 * Loads component templates from the classpath and caches the parsed {@link TemplateRenderer}.
 *
 * <p>The template file name is derived from the {@code @MedleyComponent} value:
 * {@code <templateLocation>/<name>.html}. Parsing happens once and is reused across renders
 * and across sessions.</p>
 */
public class TemplateRegistry {

    private final String templateLocation;
    private final Map<String, TemplateRenderer> cache = new ConcurrentHashMap<>();

    public TemplateRegistry(String templateLocation) {
        this.templateLocation = templateLocation.endsWith("/") ? templateLocation : templateLocation + "/";
    }

    public TemplateRenderer rendererFor(Class<?> componentClass) {
        MedleyComponent ann = componentClass.getAnnotation(MedleyComponent.class);
        if (ann == null) {
            throw new TemplateException(componentClass.getName() + " is not @MedleyComponent");
        }
        String name = ann.value();
        return cache.computeIfAbsent(name, this::load);
    }

    private TemplateRenderer load(String name) {
        String path = templateLocation + name + ".html";
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new TemplateException("Template not found on classpath: " + path);
        }
        try (InputStream in = resource.getInputStream()) {
            String template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return TemplateRenderer.of(template);
        } catch (IOException e) {
            throw new TemplateException("Failed to read template: " + path, e);
        }
    }
}
