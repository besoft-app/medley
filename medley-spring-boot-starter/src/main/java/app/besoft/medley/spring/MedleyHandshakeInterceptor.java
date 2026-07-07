package app.besoft.medley.spring;

import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.http.HttpSession;

/**
 * Copies the {@link MedleySession} (and the HTTP session id) from the HTTP session into the
 * WebSocket attributes during the handshake, so the socket handler can find the live
 * component tree created during SSR.
 *
 * <p>This is what ties the WebSocket back to the same server-side state as the page that
 * opened it. Authentication carried on the HTTP session therefore also flows through here.</p>
 */
public class MedleyHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpSession httpSession = servletRequest.getServletRequest().getSession(false);
            if (httpSession != null) {
                Object medley = httpSession.getAttribute(MedleySession.class.getName());
                if (medley != null) {
                    attributes.put(MedleySession.class.getName(), medley);
                }
                attributes.put("httpSessionId", httpSession.getId());
            }
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}
