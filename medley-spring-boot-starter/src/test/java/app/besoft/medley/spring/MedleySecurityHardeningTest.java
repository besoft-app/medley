package app.besoft.medley.spring;

import static org.assertj.core.api.Assertions.assertThat;

import app.besoft.medley.spring.fixtures.TestApplication;

import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Inbound hardening — Stage 4 increment 5c.
 *
 * <ul>
 *   <li><b>Cross-session isolation:</b> {@code componentId} is resolved only within the connecting
 *       socket's own session, so one session cannot drive another session's component (it gets a
 *       {@code reload} instead) — the session-scoped instance map is the trust boundary.</li>
 *   <li><b>Message-size cap:</b> a frame whose payload exceeds
 *       {@code medley.security.max-message-bytes} is rejected with an error before it is parsed.</li>
 * </ul>
 */
@SpringBootTest(classes = TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "medley.security.max-message-bytes=200")
class MedleySecurityHardeningTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private final ObjectMapper mapper = new ObjectMapper();

    private WebSocketSession openSocket(BlockingQueue<String> inbound, String cookie) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        return new StandardWebSocketClient()
                .execute(new TextWebSocketHandler() {
                    @Override
                    protected void handleTextMessage(WebSocketSession s, TextMessage m) {
                        inbound.add(m.getPayload());
                    }
                }, headers, URI.create("ws://localhost:" + port + "/medley/ws"))
                .get(5, TimeUnit.SECONDS);
    }

    @Test
    void componentIdFromAnotherSessionIsNotResolved() throws Exception {
        // Session A visits /cards -> its session holds a nested child "root.1::counter-card".
        rest.getForEntity("/cards", String.class);
        // Session B visits /counter -> a *different* session that has only "root".
        ResponseEntity<String> b = rest.getForEntity("/counter", String.class);
        String cookieB = b.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];

        BlockingQueue<String> inbound = new LinkedBlockingQueue<>();
        WebSocketSession ws = openSocket(inbound, cookieB);
        try {
            // B addresses a child instance that only session A has -> B's session has no such id.
            ws.sendMessage(new TextMessage(
                    "{\"componentId\":\"root.1::counter-card\",\"action\":\"increment\"}"));
            String raw = inbound.poll(5, TimeUnit.SECONDS);
            assertThat(raw).isNotNull();
            JsonNode node = mapper.readTree(raw);
            assertThat(node.isArray()).as("must not resolve another session's component").isFalse();
            assertThat(node.get("op").asText())
                    .as("cross-session id -> reload, never a foreign component action")
                    .isEqualTo("reload");
        } finally {
            ws.close();
        }
    }

    @Test
    void oversizedInboundMessageIsRejected() throws Exception {
        ResponseEntity<String> ssr = rest.getForEntity("/counter", String.class);
        String cookie = ssr.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];

        BlockingQueue<String> inbound = new LinkedBlockingQueue<>();
        WebSocketSession ws = openSocket(inbound, cookie);
        try {
            // A well-formed message padded past the 200-char cap -> rejected before parsing.
            String big = "{\"componentId\":\"root\",\"action\":\"increment\",\"args\":[\""
                    + "x".repeat(300) + "\"]}";
            ws.sendMessage(new TextMessage(big));
            JsonNode node = mapper.readTree(inbound.poll(5, TimeUnit.SECONDS));
            assertThat(node.isArray()).isFalse();
            assertThat(node.get("op").asText()).isEqualTo("error");
            assertThat(node.get("message").asText()).contains("too large");
        } finally {
            ws.close();
        }
    }
}
