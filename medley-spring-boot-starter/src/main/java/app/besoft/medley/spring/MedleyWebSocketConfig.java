package app.besoft.medley.spring;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the Medley WebSocket endpoint at the configured path with the handshake
 * interceptor that bridges the HTTP session.
 *
 * <p>This is a {@code @Configuration} class (imported by {@link MedleyAutoConfiguration}) rather
 * than a plain bean, so that {@code @EnableWebSocket}'s imported infrastructure is actually
 * processed — otherwise the endpoint would never be mapped and handshakes would 404.</p>
 *
 * <p>Origin policy (Stage 4, increment 5a): by default only same-origin handshakes are accepted
 * (Spring's default when no origins are configured), which closes the cross-site WebSocket-hijacking
 * / CSRF vector. Configure {@code medley.security.allowed-origins} to permit additional origins.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSocket
public class MedleyWebSocketConfig implements WebSocketConfigurer {

    private final MedleyWebSocketHandler handler;
    private final MedleyHandshakeInterceptor interceptor;
    private final MedleyProperties properties;

    public MedleyWebSocketConfig(MedleyWebSocketHandler handler,
                                 MedleyHandshakeInterceptor interceptor,
                                 MedleyProperties properties) {
        this.handler = handler;
        this.interceptor = interceptor;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        var registration = registry.addHandler(handler, properties.getWebsocketPath())
                .addInterceptors(interceptor);
        // Empty allow-list => leave Spring's same-origin default in place (fail-closed). A configured
        // list opens exactly those origins; "*" (dev only) opens all.
        List<String> allowedOrigins = properties.getSecurity().getAllowedOrigins();
        if (!allowedOrigins.isEmpty()) {
            registration.setAllowedOrigins(allowedOrigins.toArray(new String[0]));
        }
    }
}
