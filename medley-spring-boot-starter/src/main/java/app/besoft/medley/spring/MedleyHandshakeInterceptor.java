package app.besoft.medley.spring;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.HttpStatus;
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
 *
 * <p>WS auth (Stage 4, increment 5b): the authenticated {@link Principal} (from the servlet request —
 * populated by Spring Security or container auth; no Spring Security dependency required) is bound onto
 * the socket under {@link #PRINCIPAL_ATTRIBUTE}. When {@code requireAuthenticated} is set, a handshake
 * with no principal is rejected with 401.</p>
 */
public class MedleyHandshakeInterceptor implements HandshakeInterceptor {

    /** WebSocket-attributes key under which the authenticated principal (if any) is bound. */
    public static final String PRINCIPAL_ATTRIBUTE = "medley.principal";

    private final boolean requireAuthenticated;

    public MedleyHandshakeInterceptor() {
        this(false);
    }

    public MedleyHandshakeInterceptor(boolean requireAuthenticated) {
        this.requireAuthenticated = requireAuthenticated;
    }

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

        Principal principal = request.getPrincipal();
        if (principal != null) {
            attributes.put(PRINCIPAL_ATTRIBUTE, principal);
        }
        if (requireAuthenticated && principal == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}
