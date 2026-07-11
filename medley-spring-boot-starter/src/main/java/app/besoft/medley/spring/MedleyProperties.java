package app.besoft.medley.spring;

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

    public String getWebsocketPath() { return websocketPath; }
    public void setWebsocketPath(String websocketPath) { this.websocketPath = websocketPath; }

    public String getTemplateLocation() { return templateLocation; }
    public void setTemplateLocation(String templateLocation) { this.templateLocation = templateLocation; }

    public Session getSession() { return session; }

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
}
