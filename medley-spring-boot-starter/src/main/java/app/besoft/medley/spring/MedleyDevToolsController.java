package app.besoft.medley.spring;

import app.besoft.medley.core.component.Annotations;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.core.component.ComponentInstance;
import app.besoft.medley.core.diff.IdPaths;

import jakarta.servlet.http.HttpSession;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Dev-tools: a read-only snapshot of the caller's own component tree (Stage 5, increment 2).
 *
 * <p>Serves {@code GET /medley/devtools/tree} — every live component in the <em>requesting</em>
 * HTTP session (root plus nested {@code <medley-component>} children), with its id, component name,
 * class, parent id, and the current values of its {@code @State} and {@code @Param} fields. The
 * overlay ({@code devtools.js}) renders it beside the live patch log, so a developer can see both
 * what the server holds and what it sent.</p>
 *
 * <p><b>Only the caller's session is ever read.</b> The {@link MedleySession} is taken from the
 * request's own {@code HttpSession} — there is no session id parameter, so one browser cannot ask
 * for another's state. On top of that the whole endpoint only exists when
 * {@code medley.devtools.enabled=true} (see {@link MedleyDevToolsAutoConfiguration}), because
 * {@code @State} values are application data.</p>
 *
 * <p>Reflection here is deliberately local: it mirrors core's (package-private) {@code ParamScanner}
 * rather than widening core's API for a development-only feature — {@code medley-core} is untouched
 * by this increment.</p>
 */
public class MedleyDevToolsController {

    /** Serialized value length beyond which a field is reported as elided rather than inlined. */
    private static final int MAX_VALUE_CHARS = 4096;

    private final ObjectMapper mapper;

    public MedleyDevToolsController(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** {@code GET /medley/devtools/tree} — the requesting session's component tree as JSON. */
    public ServerResponse tree(ServerRequest request) {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode components = root.putArray("components");

        MedleySession session = sessionOf(request);
        if (session != null) {
            List<String> ids = new ArrayList<>(session.componentIds());
            ids.sort(Comparator.naturalOrder());
            for (String id : ids) {
                ComponentInstance instance = session.get(id);
                if (instance != null) { // may have been evicted between the id snapshot and this read
                    components.add(describe(id, instance.component()));
                }
            }
        }
        root.put("count", components.size());

        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(root);
    }

    /** The Medley session bound to the caller's HTTP session, or null when there is none yet. */
    private MedleySession sessionOf(ServerRequest request) {
        HttpSession httpSession = request.servletRequest().getSession(false);
        if (httpSession == null) {
            return null;
        }
        Object existing = httpSession.getAttribute(MedleySession.class.getName());
        return (existing instanceof MedleySession s) ? s : null;
    }

    private ObjectNode describe(String id, Component component) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", id);
        node.put("name", componentName(component.getClass()));
        node.put("type", component.getClass().getSimpleName());
        // Nested children carry their owner in their id (host slot + "::" + name); the root has none.
        node.put("parent", IdPaths.ownerComponentId(id));
        node.set("state", fields(component, Annotations.State.class));
        node.set("params", fields(component, Annotations.Param.class));
        return node;
    }

    private static String componentName(Class<?> cls) {
        Annotations.MedleyComponent ann = cls.getAnnotation(Annotations.MedleyComponent.class);
        return ann != null ? ann.value() : cls.getSimpleName();
    }

    /** Current values of the fields carrying the given annotation, walking the class hierarchy. */
    private ObjectNode fields(Component component, Class<? extends java.lang.annotation.Annotation> marker) {
        ObjectNode out = mapper.createObjectNode();
        Class<?> cls = component.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (f.isAnnotationPresent(marker) && !out.has(f.getName())) {
                    out.set(f.getName(), read(component, f));
                }
            }
            cls = cls.getSuperclass();
        }
        return out;
    }

    /**
     * A single field value as JSON. A value that Jackson cannot serialize (or that is unreasonably
     * large) degrades to a short descriptive string — the inspector must never fail the request over
     * one awkward field.
     */
    private JsonNode read(Component component, Field field) {
        Object value;
        try {
            field.setAccessible(true);
            value = field.get(component);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return new TextNode("<unreadable>");
        }
        if (value == null) {
            return mapper.nullNode();
        }
        JsonNode json;
        try {
            json = mapper.valueToTree(value);
        } catch (RuntimeException e) {
            return new TextNode("<" + value.getClass().getSimpleName() + ": not serializable>");
        }
        if (json.toString().length() > MAX_VALUE_CHARS) {
            return new TextNode("<" + value.getClass().getSimpleName() + ": too large to inline>");
        }
        return json;
    }
}
