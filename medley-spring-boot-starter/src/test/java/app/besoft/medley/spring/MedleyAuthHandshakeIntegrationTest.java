package app.besoft.medley.spring;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.besoft.medley.spring.fixtures.TestApplication;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * WS auth gate end-to-end — Stage 4, increment 5b. With
 * {@code medley.security.require-authenticated-handshake=true} and no authentication configured, the
 * handshake is rejected (the {@code TestApplication} has no security, so the request has no principal).
 */
@SpringBootTest(classes = TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "medley.security.require-authenticated-handshake=true")
class MedleyAuthHandshakeIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Test
    void unauthenticatedHandshakeIsRejectedWhenAuthRequired() {
        rest.getForEntity("/counter", String.class); // establish a Medley session first
        assertThatThrownBy(() -> new StandardWebSocketClient()
                .execute(new TextWebSocketHandler() { }, new WebSocketHttpHeaders(),
                        URI.create("ws://localhost:" + port + "/medley/ws"))
                .get(5, TimeUnit.SECONDS))
                .as("an unauthenticated handshake must be rejected when auth is required");
    }
}
