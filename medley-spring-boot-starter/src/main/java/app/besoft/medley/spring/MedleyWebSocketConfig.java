package app.besoft.medley.spring;

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
        registry.addHandler(handler, properties.getWebsocketPath())
                .addInterceptors(interceptor)
                .setAllowedOriginPatterns("*"); // PoC: tighten in production (see design doc §8)
    }
}
