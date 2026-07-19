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
 * Reconnect resync over a real Spring WebSocket — Stage 4, increment 6b.
 *
 * <p>A {@code {"type":"resync"}} message (which the client sends on a reconnect) makes the server
 * re-render the root fresh and reply with a single {@code replace} of the root subtree. The reply must
 * reflect the <em>current</em> state, including a nested child's state that changed via its own action —
 * proving the resync re-renders rather than replaying a stale root tree — so a client that missed
 * patches while disconnected is brought back into a consistent DOM.</p>
 */
@SpringBootTest(classes = TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MedleyResyncIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void resyncReplacesRootWithCurrentStateIncludingChildren() throws Exception {
        ResponseEntity<String> ssr = rest.getForEntity("/cards", String.class);
        assertThat(ssr.getBody()).contains(">10</medley-text>");
        String cookie = ssr.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];

        BlockingQueue<String> inbound = new LinkedBlockingQueue<>();
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        WebSocketSession ws = new StandardWebSocketClient()
                .execute(new TextWebSocketHandler() {
                    @Override
                    protected void handleTextMessage(WebSocketSession s, TextMessage m) {
                        inbound.add(m.getPayload());
                    }
                }, headers, URI.create("ws://localhost:" + port + "/medley/ws"))
                .get(5, TimeUnit.SECONDS);

        try {
            // A child action advances only the child's own state (10 -> 11); the root's last tree
            // still embeds the old child subtree.
            ws.sendMessage(new TextMessage(
                    "{\"componentId\":\"root.1::counter-card\",\"action\":\"increment\"}"));
            assertThat(readJson(inbound).get(0).get("value").asText()).isEqualTo("11");

            // Resync -> one replace of the root whose HTML reflects the child's *current* state (11),
            // not the stale root tree. This is what a reconnect fires to recover a drifted DOM.
            ws.sendMessage(new TextMessage("{\"type\":\"resync\"}"));
            JsonNode patches = readJson(inbound);
            assertThat(patches.isArray()).isTrue();
            assertThat(patches).hasSize(1);
            assertThat(patches.get(0).get("op").asText()).isEqualTo("replace");
            assertThat(patches.get(0).get("id").asText()).isEqualTo("root");
            assertThat(patches.get(0).get("html").asText())
                    .as("resync HTML must reflect the child's current state")
                    .contains(">11</medley-text>")
                    .contains("data-medley-cid=\"root.1::counter-card\"");
        } finally {
            ws.close();
        }
    }

    private JsonNode readJson(BlockingQueue<String> inbound) throws Exception {
        String raw = inbound.poll(5, TimeUnit.SECONDS);
        assertThat(raw).isNotNull();
        return mapper.readTree(raw);
    }
}
