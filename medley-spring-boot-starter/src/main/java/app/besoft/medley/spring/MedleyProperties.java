package app.besoft.medley.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration bound from {@code medley.*} in application.yml. */
@ConfigurationProperties(prefix = "medley")
public class MedleyProperties {

    /** WebSocket endpoint path for events and patches. */
    private String websocketPath = "/medley/ws";

    /** Classpath location of component templates. */
    private String templateLocation = "templates/medley/";

    public String getWebsocketPath() { return websocketPath; }
    public void setWebsocketPath(String websocketPath) { this.websocketPath = websocketPath; }

    public String getTemplateLocation() { return templateLocation; }
    public void setTemplateLocation(String templateLocation) { this.templateLocation = templateLocation; }
}
