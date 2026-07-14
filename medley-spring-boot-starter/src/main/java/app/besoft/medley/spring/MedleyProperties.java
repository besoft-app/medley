package app.besoft.medley.spring;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration bound from {@code medley.*} in application.yml. */
@ConfigurationProperties(prefix = "medley")
public class MedleyProperties {

    /** WebSocket endpoint path for events and patches. */
    private String websocketPath = "/medley/ws";

    /** Classpath location of component templates. */
    private String templateLocation = "templates/medley/";

    /** Per-session limits (bound from {@code medley.session.*}). */
    private final Session session = new Session();

    /** WebSocket security (bound from {@code medley.security.*}). */
    private final Security security = new Security();

    /** Development tools (bound from {@code medley.devtools.*}). */
    private final DevTools devtools = new DevTools();

    public String getWebsocketPath() { return websocketPath; }
    public void setWebsocketPath(String websocketPath) { this.websocketPath = websocketPath; }

    public String getTemplateLocation() { return templateLocation; }
    public void setTemplateLocation(String templateLocation) { this.templateLocation = templateLocation; }

    public Session getSession() { return session; }

    public Security getSecurity() { return security; }

    public DevTools getDevtools() { return devtools; }

    /** {@code medley.session.*}. */
    public static class Session {

        /**
         * Cap on the number of live component instances per session (root + all nested children), a
         * runaway/DoS guard. Mounting beyond it fails fast. {@code 0} or negative means unlimited.
         */
        private int maxComponents = 2000;

        public int getMaxComponents() { return maxComponents; }
        public void setMaxComponents(int maxComponents) { this.maxComponents = maxComponents; }
    }

    /** {@code medley.security.*}. */
    public static class Security {

        /**
         * Allowed WebSocket handshake origins (Stage 4, increment 5a). <b>Empty (the default) means
         * same-origin only</b> — a cross-origin handshake is rejected, closing the cross-site
         * WebSocket-hijacking / CSRF vector. Add trusted origins (e.g. {@code https://app.example.com})
         * to permit them; the single value {@code "*"} allows all (development only).
         */
        private List<String> allowedOrigins = new ArrayList<>();

        /**
         * Maximum inbound WebSocket text-message length (Stage 4, increment 5c). A frame whose payload
         * length exceeds this is rejected with an error before being parsed or processed, bounding the
         * work an unauthenticated frame can trigger. {@code 0} or negative disables the check. (The
         * servlet container also enforces its own text buffer limit beneath this.)
         */
        private int maxMessageBytes = 65536;

        /**
         * Require an authenticated principal on the WebSocket handshake (Stage 4, increment 5b). When
         * {@code true}, a handshake with no authenticated user is rejected (401). Default {@code false}
         * — apps without authentication (or that already gate the SSR route) are unaffected. The
         * principal, when present, is always bound onto the socket regardless of this flag.
         */
        private boolean requireAuthenticatedHandshake = false;

        public List<String> getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(List<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }

        public int getMaxMessageBytes() { return maxMessageBytes; }
        public void setMaxMessageBytes(int maxMessageBytes) { this.maxMessageBytes = maxMessageBytes; }

        public boolean isRequireAuthenticatedHandshake() { return requireAuthenticatedHandshake; }
        public void setRequireAuthenticatedHandshake(boolean requireAuthenticatedHandshake) {
            this.requireAuthenticatedHandshake = requireAuthenticatedHandshake;
        }
    }

    /** {@code medley.devtools.*}. */
    public static class DevTools {

        /**
         * Enable the development tools (Stage 5, increment 2): the in-page patch/tree inspector overlay
         * and the {@code /medley/devtools/tree} snapshot endpoint. <b>Default {@code false}</b>, and it
         * must stay that way outside development: the snapshot exposes a session's {@code @State} and
         * {@code @Param} values, which are application data. When disabled, neither the endpoint nor the
         * overlay script exists — the SSR shell does not reference it.
         */
        private boolean enabled = false;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
