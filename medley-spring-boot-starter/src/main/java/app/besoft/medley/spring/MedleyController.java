package app.besoft.medley.spring;

import app.besoft.medley.core.component.Component;
import app.besoft.medley.core.component.ComponentInstance;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the first page load (server-side rendering).
 *
 * <p>Flow: resolve the route to a component class, create a fresh bean, mount it in the
 * session, render its initial HTML, and wrap it in a shell that loads {@code medley.js}.
 * The client then opens the WebSocket and hydrates the already-rendered DOM.</p>
 */
@RestController
public class MedleyController {

    private final RouteRegistry routes;
    private final TemplateRegistry templates;
    private final MedleyProperties properties;

    public MedleyController(RouteRegistry routes, TemplateRegistry templates, MedleyProperties properties) {
        this.routes = routes;
        this.templates = templates;
        this.properties = properties;
    }

    @GetMapping(path = "/**", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> render(HttpServletRequest request) {
        String path = request.getRequestURI();
        Class<? extends Component> compClass = routes.resolve(path);
        if (compClass == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.TEXT_HTML)
                    .body(notFound(path));
        }

        HttpSession httpSession = request.getSession(true);
        MedleySession session = sessionFor(httpSession);

        String componentId = "root";
        Component component = routes.newComponent(compClass);
        ComponentInstance instance = session.mount(componentId, component);
        String bodyHtml = instance.renderInitialHtml();

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(shell(bodyHtml));
    }

    private MedleySession sessionFor(HttpSession httpSession) {
        Object existing = httpSession.getAttribute(MedleySession.class.getName());
        if (existing instanceof MedleySession s) {
            return s;
        }
        MedleySession s = new MedleySession(templates);
        httpSession.setAttribute(MedleySession.class.getName(), s);
        return s;
    }

    private String shell(String bodyHtml) {
        return """
            <!doctype html>
            <html lang="pl">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Medley</title>
              <style>
                body { font-family: system-ui, sans-serif; margin: 3rem; color: #1a1a1a; }
                .counter { display: flex; gap: .75rem; align-items: center; font-size: 1.25rem; }
                button { font-size: 1rem; padding: .4rem .8rem; cursor: pointer; border-radius: .4rem;
                         border: 1px solid #888; background: #f6f6f6; }
                button:hover { background: #ececec; }
                medley-placeholder { display: none; }
              </style>
            </head>
            <body>
              <div id="medley-root" data-ws="%s">%s</div>
              <script src="/medley/medley.js"></script>
            </body>
            </html>
            """.formatted(properties.getWebsocketPath(), bodyHtml);
    }

    private String notFound(String path) {
        return "<!doctype html><html><body><h1>404</h1><p>No Medley route for "
                + escape(path) + "</p></body></html>";
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
