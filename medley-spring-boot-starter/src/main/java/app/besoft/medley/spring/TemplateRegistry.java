package app.besoft.medley.spring;

import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.core.component.ParamBinder;
import app.besoft.medley.core.template.ChildComponentFactory;
import app.besoft.medley.core.template.TemplateException;
import app.besoft.medley.core.template.TemplateNode;
import app.besoft.medley.core.template.TemplateParser;
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
 *
 * <p>Reusable library fragments referenced by {@code <medley-partial name="x">} are loaded from
 * {@code <templateLocation>/partials/<x>.html} and supplied to the renderer as a
 * {@link app.besoft.medley.core.template.PartialResolver}. Both templates and partials are parsed
 * once and cached.</p>
 */
public class TemplateRegistry {

    private final String templateLocation;
    private final ComponentRegistry components;
    private final Map<String, TemplateRenderer> cache = new ConcurrentHashMap<>();
    private final Map<String, TemplateNode.Element> partialCache = new ConcurrentHashMap<>();

    public TemplateRegistry(String templateLocation) {
        this(templateLocation, null);
    }

    public TemplateRegistry(String templateLocation, ComponentRegistry components) {
        this.templateLocation = templateLocation.endsWith("/") ? templateLocation : templateLocation + "/";
        this.components = components;
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
            return TemplateRenderer.of(template, this::resolvePartial, this::createChild);
        } catch (IOException e) {
            throw new TemplateException("Failed to read template: " + path, e);
        }
    }

    /** {@link ChildComponentFactory}: mount a fresh {@code @MedleyChild}, inject its {@code @Param}s
     *  from the parent, start its lifecycle, and pair it with its template renderer. Null when the
     *  name is unregistered (the renderer then raises a named {@link TemplateException}). */
    private ChildComponentFactory.Child createChild(String name, Map<String, Object> params) {
        Component child = components == null ? null : components.newInstance(name);
        if (child == null) {
            return null;
        }
        ParamBinder.inject(child, params);
        child.onInit();
        return new ChildComponentFactory.Child(child, rendererFor(child.getClass()));
    }

    /** {@link app.besoft.medley.core.template.PartialResolver}: parsed fragment root, or null. */
    private TemplateNode.Element resolvePartial(String name) {
        return partialCache.computeIfAbsent(name, this::loadPartial);
    }

    private TemplateNode.Element loadPartial(String name) {
        String path = templateLocation + "partials/" + name + ".html";
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            return null; // unknown partial -> renderer raises a TemplateException naming it
        }
        try (InputStream in = resource.getInputStream()) {
            return TemplateParser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new TemplateException("Failed to read partial: " + path, e);
        }
    }
}
