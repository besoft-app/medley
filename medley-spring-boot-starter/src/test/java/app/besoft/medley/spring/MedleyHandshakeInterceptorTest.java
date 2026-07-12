package app.besoft.medley.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

/**
 * WS auth on the handshake — Stage 4, increment 5b. The authenticated principal is bound onto the
 * socket, and (when required) an unauthenticated handshake is rejected with 401. A plain
 * {@code ServerHttpRequest} mock skips the servlet-session copy (covered by the integration tests),
 * isolating the principal/auth logic.
 */
class MedleyHandshakeInterceptorTest {

    private final WebSocketHandler wsHandler = mock(WebSocketHandler.class);
    private static final Principal ALICE = () -> "alice";

    private boolean handshake(MedleyHandshakeInterceptor interceptor, Principal principal,
                              ServerHttpResponse response, Map<String, Object> attributes) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getPrincipal()).thenReturn(principal);
        return interceptor.beforeHandshake(request, response, wsHandler, attributes);
    }

    @Test
    void bindsPrincipalWhenPresent() {
        Map<String, Object> attributes = new HashMap<>();
        boolean ok = handshake(new MedleyHandshakeInterceptor(false), ALICE,
                mock(ServerHttpResponse.class), attributes);

        assertThat(ok).isTrue();
        assertThat(attributes).containsEntry(MedleyHandshakeInterceptor.PRINCIPAL_ATTRIBUTE, ALICE);
    }

    @Test
    void allowsUnauthenticatedByDefault() {
        Map<String, Object> attributes = new HashMap<>();
        boolean ok = handshake(new MedleyHandshakeInterceptor(false), null,
                mock(ServerHttpResponse.class), attributes);

        assertThat(ok).isTrue();
        assertThat(attributes).doesNotContainKey(MedleyHandshakeInterceptor.PRINCIPAL_ATTRIBUTE);
    }

    @Test
    void rejectsUnauthenticatedWhenRequired() {
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        boolean ok = handshake(new MedleyHandshakeInterceptor(true), null, response, new HashMap<>());

        assertThat(ok).as("no principal + required -> reject").isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void allowsAuthenticatedWhenRequired() {
        Map<String, Object> attributes = new HashMap<>();
        boolean ok = handshake(new MedleyHandshakeInterceptor(true), ALICE,
                mock(ServerHttpResponse.class), attributes);

        assertThat(ok).isTrue();
        assertThat(attributes).containsEntry(MedleyHandshakeInterceptor.PRINCIPAL_ATTRIBUTE, ALICE);
    }
}
