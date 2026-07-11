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
 * End-to-end proof of nested-child eviction over a real Spring WebSocket — 4b.3b.
 *
 * <p>A routed {@code /toggle-child} host shows/hides a stateful {@code counter-card} child behind an
 * {@code *if}. Hiding it structurally removes the boundary; the child instance must be evicted (so an
 * action addressed to it now gets a {@code reload}), and showing it again mounts a fresh instance whose
 * count starts over — proving the old one was freed, not merely detached.</p>
 */
@SpringBootTest(classes = TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MedleyChildEvictionIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private final ObjectMapper mapper = new ObjectMapper();

    private static final String CHILD_CID = "root.1::counter-card";
    private static final String CHILD_INCREMENT =
            "{\"componentId\":\"" + CHILD_CID + "\",\"action\":\"increment\"}";
    private static final String TOGGLE = "{\"componentId\":\"root\",\"action\":\"toggle\"}";

    @Test
    void hidingAChildEvictsItAndShowingItMountsAFreshInstance() throws Exception {
        ResponseEntity<String> ssr = rest.getForEntity("/toggle-child", String.class);
        assertThat(ssr.getStatusCode().value()).isEqualTo(200);
        assertThat(ssr.getBody()).contains("data-medley-cid=\"" + CHILD_CID + "\"");
        String sessionCookie = ssr.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];

        BlockingQueue<String> inbound = new LinkedBlockingQueue<>();
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add(HttpHeaders.COOKIE, sessionCookie);
        WebSocketSession ws = new StandardWebSocketClient()
                .execute(new TextWebSocketHandler() {
                    @Override
                    protected void handleTextMessage(WebSocketSession s, TextMessage m) {
                        inbound.add(m.getPayload());
                    }
                }, headers, URI.create("ws://localhost:" + port + "/medley/ws"))
                .get(5, TimeUnit.SECONDS);

        try {
            // 1. Child counts up from its seed: 10 -> 11.
            ws.sendMessage(new TextMessage(CHILD_INCREMENT));
            JsonNode first = readJson(inbound);
            assertThat(first.isArray()).isTrue();
            assertThat(first.get(0).get("value").asText()).isEqualTo("11");

            // 2. Hide the child (*if false) -> its boundary is Replaced -> the instance is evicted.
            ws.sendMessage(new TextMessage(TOGGLE));
            readJson(inbound); // host re-render (the Replace); content asserted indirectly below

            // 3. An action addressed to the now-evicted child yields a reload control object.
            ws.sendMessage(new TextMessage(CHILD_INCREMENT));
            JsonNode afterEvict = readJson(inbound);
            assertThat(afterEvict.isArray()).as("evicted child -> control object, not patches").isFalse();
            assertThat(afterEvict.get("op").asText()).isEqualTo("reload");

            // 4. Show it again -> a fresh instance mounts, count seeded back to 10.
            ws.sendMessage(new TextMessage(TOGGLE));
            readJson(inbound);

            // 5. Incrementing the fresh child starts over at 10 -> 11 (not 11 -> 12).
            ws.sendMessage(new TextMessage(CHILD_INCREMENT));
            JsonNode fresh = readJson(inbound);
            assertThat(fresh.isArray()).isTrue();
            assertThat(fresh.get(0).get("id").asText()).startsWith(CHILD_CID);
            assertThat(fresh.get(0).get("value").asText())
                    .as("fresh instance after re-show starts from the seed, proving the old was evicted")
                    .isEqualTo("11");
        } finally {
            ws.close();
        }
    }

    private JsonNode readJson(BlockingQueue<String> inbound) throws Exception {
        String raw = inbound.poll(5, TimeUnit.SECONDS);
        assertThat(raw).as("expected a WS response").isNotNull();
        return mapper.readTree(raw);
    }
}
