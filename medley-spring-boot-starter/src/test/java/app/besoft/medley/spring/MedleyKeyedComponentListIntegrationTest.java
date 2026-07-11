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
 * End-to-end proof of a keyed {@code *for} of nested components over a real Spring WebSocket — 4b.3c.
 *
 * <p>A routed {@code /card-list} host renders a keyed list of stateful {@code counter-card} children.
 * Each row is its own instance at {@code hostId[key]::name}; adding a row mounts a new child without
 * disturbing existing ones, removing a row evicts its child (4b.3b), and a pure reorder is a diff no-op
 * (no {@code move} op — §4.3) so no boundary patch is emitted and every instance is preserved.</p>
 */
@SpringBootTest(classes = TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MedleyKeyedComponentListIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private final ObjectMapper mapper = new ObjectMapper();

    private static final String CID_A = "root.1.0[a]::counter-card";
    private static final String CID_B = "root.1.0[b]::counter-card";

    private static String incr(String cid) {
        return "{\"componentId\":\"" + cid + "\",\"action\":\"increment\"}";
    }

    @Test
    void keyedComponentListMountsAddsRemovesAndReordersPerKey() throws Exception {
        ResponseEntity<String> ssr = rest.getForEntity("/card-list", String.class);
        assertThat(ssr.getStatusCode().value()).isEqualTo(200);
        String html = ssr.getBody();
        // Each row is its own keyed child instance, seeded from its row.
        assertThat(html).contains("data-medley-cid=\"" + CID_A + "\"");
        assertThat(html).contains("data-medley-cid=\"" + CID_B + "\"");
        assertThat(html).contains("count: 10").contains("count: 20");
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
            // 1. Row 'a' counts up on its own: 10 -> 11.
            ws.sendMessage(new TextMessage(incr(CID_A)));
            assertThat(readJson(inbound).get(0).get("value").asText()).isEqualTo("11");

            // 2. Add a row -> an Insert of the new keyed boundary host (which carries the child HTML).
            ws.sendMessage(new TextMessage("{\"componentId\":\"root\",\"action\":\"addRow\"}"));
            JsonNode addPatches = readJson(inbound);
            assertThat(addPatches).anySatisfy(p -> {
                assertThat(p.get("op").asText()).isEqualTo("insert");
                assertThat(p.get("id").asText()).isEqualTo("root.1.0[c]");
            });

            // 3. Row 'a' survived the add (still its own instance): 11 -> 12, not reset to 10 -> 11.
            ws.sendMessage(new TextMessage(incr(CID_A)));
            assertThat(readJson(inbound).get(0).get("value").asText()).isEqualTo("12");

            // 4. Remove row 'b' -> its child is evicted; an action addressed to it now gets a reload.
            ws.sendMessage(new TextMessage("{\"componentId\":\"root\",\"action\":\"removeRow\",\"args\":[\"b\"]}"));
            readJson(inbound); // the Remove patch
            ws.sendMessage(new TextMessage(incr(CID_B)));
            JsonNode afterRemove = readJson(inbound);
            assertThat(afterRemove.isArray()).as("evicted row child -> reload control object").isFalse();
            assertThat(afterRemove.get("op").asText()).isEqualTo("reload");

            // 5. Reorder -> pure keyed reorder is a diff no-op: no boundary insert/remove/replace.
            ws.sendMessage(new TextMessage("{\"componentId\":\"root\",\"action\":\"reorder\"}"));
            JsonNode reorderPatches = readJson(inbound);
            assertThat(reorderPatches).allSatisfy(p -> {
                String op = p.get("op").asText();
                if (op.equals("insert") || op.equals("remove") || op.equals("replace")) {
                    assertThat(p.get("id").asText())
                            .as("a pure reorder must not add/remove/replace a component boundary")
                            .doesNotContain("[");
                }
            });
            // 6. And row 'a' is still alive after the reorder (12 -> 13).
            ws.sendMessage(new TextMessage(incr(CID_A)));
            assertThat(readJson(inbound).get(0).get("value").asText()).isEqualTo("13");
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
