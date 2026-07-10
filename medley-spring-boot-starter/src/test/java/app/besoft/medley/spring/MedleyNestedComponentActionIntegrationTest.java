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
 * End-to-end proof of nested-component action routing over a real Spring WebSocket — 4b.2.
 *
 * <p>A routed {@code /cards} host nests a stateful {@code counter-card} child. Two properties:
 * <ol>
 *   <li><b>Child action routes to the child.</b> An {@code increment} addressed to the child
 *       instance id ({@code root.1::counter-card}) produces exactly one text patch, addressed inside
 *       the child — the golden minimal diff for a nested component.</li>
 *   <li><b>The boundary is opaque + the child persists.</b> A host {@code bump} re-renders the host
 *       only (no patch inside the child), and a subsequent child {@code increment} continues from the
 *       child's own state (11 → 12, not reset) — so the child instance survived the parent
 *       re-render.</li>
 * </ol>
 */
@SpringBootTest(classes = TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MedleyNestedComponentActionIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private final ObjectMapper mapper = new ObjectMapper();

    private static final String CHILD_CID = "root.1::counter-card";
    private static final String CHILD_INCREMENT =
            "{\"componentId\":\"" + CHILD_CID + "\",\"action\":\"increment\"}";
    private static final String HOST_BUMP =
            "{\"componentId\":\"root\",\"action\":\"bump\"}";

    @Test
    void childActionRoutesToChildAndBoundaryIsOpaque() throws Exception {
        // 1. SSR — mounts the host and, under it, the child instance (registered by its cid).
        ResponseEntity<String> ssr = rest.getForEntity("/cards", String.class);
        assertThat(ssr.getStatusCode().value()).isEqualTo(200);
        String html = ssr.getBody();
        assertThat(html).contains("data-medley-cid=\"" + CHILD_CID + "\"");
        assertThat(html).as("child seeded from :start=seed").contains("count: 10");

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
            // 2. Child action -> exactly one text patch, addressed inside the child (10 -> 11).
            ws.sendMessage(new TextMessage(CHILD_INCREMENT));
            JsonNode patches = readPatches(inbound);
            assertThat(patches).hasSize(1);
            assertThat(patches.get(0).get("op").asText()).isEqualTo("text");
            assertThat(patches.get(0).get("id").asText())
                    .as("patch is addressed inside the child instance")
                    .startsWith(CHILD_CID);
            assertThat(patches.get(0).get("value").asText()).isEqualTo("11");

            // 3. Host action -> re-renders the host only; NO patch touches the child (opaque boundary).
            ws.sendMessage(new TextMessage(HOST_BUMP));
            JsonNode hostPatches = readPatches(inbound);
            assertThat(hostPatches).allSatisfy(p ->
                    assertThat(p.get("id").asText())
                            .as("a host re-render must not touch the child's DOM")
                            .doesNotStartWith(CHILD_CID));
            assertThat(hostPatches).anySatisfy(p -> {
                assertThat(p.get("op").asText()).isEqualTo("text");
                assertThat(p.get("value").asText()).isEqualTo("1"); // hostClicks 0 -> 1
            });

            // 4. Child action again -> continues from the child's own state (11 -> 12), proving the
            //    child instance survived the host re-render.
            ws.sendMessage(new TextMessage(CHILD_INCREMENT));
            JsonNode again = readPatches(inbound);
            assertThat(again).hasSize(1);
            assertThat(again.get(0).get("id").asText()).startsWith(CHILD_CID);
            assertThat(again.get(0).get("value").asText())
                    .as("child @State persisted across the parent re-render")
                    .isEqualTo("12");
        } finally {
            ws.close();
        }
    }

    private JsonNode readPatches(BlockingQueue<String> inbound) throws Exception {
        String raw = inbound.poll(5, TimeUnit.SECONDS);
        assertThat(raw).as("expected a patch response").isNotNull();
        JsonNode node = mapper.readTree(raw);
        assertThat(node.isArray()).as("response must be a patch array, was: " + raw).isTrue();
        return node;
    }
}
