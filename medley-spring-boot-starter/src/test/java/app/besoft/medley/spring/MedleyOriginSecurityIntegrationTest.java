package app.besoft.medley.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.besoft.medley.spring.fixtures.TestApplication;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Origin allow-list on the WebSocket handshake — 4b/Stage 4 increment 5a.
 *
 * <p>With {@code medley.security.allowed-origins} configured, a handshake whose {@code Origin} is on
 * the list is accepted and one with a foreign {@code Origin} is rejected — closing the cross-site
 * WebSocket-hijacking / CSRF vector. (The default, an empty list, is same-origin only.)</p>
 */
@SpringBootTest(classes = TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "medley.security.allowed-origins=https://allowed.example")
class MedleyOriginSecurityIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private WebSocketSession connectWithOrigin(String origin) throws Exception {
        // SSR first so a Medley session exists (not required for the handshake origin check, but keeps
        // the connection realistic).
        rest.getForEntity("/counter", String.class);
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add(HttpHeaders.ORIGIN, origin);
        return new StandardWebSocketClient()
                .execute(new TextWebSocketHandler() { }, headers,
                        URI.create("ws://localhost:" + port + "/medley/ws"))
                .get(5, TimeUnit.SECONDS);
    }

    @Test
    void handshakeFromAllowedOriginIsAccepted() throws Exception {
        WebSocketSession ws = connectWithOrigin("https://allowed.example");
        try {
            assertThat(ws.isOpen()).isTrue();
        } finally {
            ws.close();
        }
    }

    @Test
    void handshakeFromForeignOriginIsRejected() {
        assertThatThrownBy(() -> connectWithOrigin("https://evil.example"))
                .as("a cross-origin handshake must be rejected");
    }
}
