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
 * End-to-end proof of children projection over a real Spring WebSocket — Stage 6, increment 6.2.
 *
 * <p>{@code /panel} projects the parent's {@code @State} into a child's {@code <medley-slot>}. Two
 * symmetric guarantees are locked here: a parent action that changes projected content emits
 * <b>exactly one</b> patch addressed in the <b>parent's</b> id-space (even though the node sits inside
 * the child's DOM), and a child action emits exactly one patch inside the <b>child</b>, never touching
 * projected content.</p>
 */
@SpringBootTest(classes = TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MedleyDefaultSlotIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private static final String CHILD_CID = "root.1::slot-panel";
    private static final String PROJECTED_TEXT_ID = "root.1.0.0";
    private static final String BUMP = "{\"componentId\":\"root\",\"action\":\"bump\"}";
    private static final String NUDGE = "{\"componentId\":\"root\",\"action\":\"nudge\"}";
    private static final String TICK = "{\"componentId\":\"" + CHILD_CID + "\",\"action\":\"tick\"}";

    @Test
    void ssrEmbedsProjectedContentInsideTheChildBoundary() {
        ResponseEntity<String> ssr = rest.getForEntity("/panel", String.class);
        assertThat(ssr.getStatusCode().value()).isEqualTo(200);
        assertThat(ssr.getBody()).contains("data-medley-cid=\"" + CHILD_CID + "\"");
        assertThat(ssr.getBody()).as("the slot is a real, addressable element")
                .contains("<medley-slot data-medley-id=\"" + CHILD_CID + ".1\"");
        assertThat(ssr.getBody()).as("projected events must route to the parent")
                .contains("data-medley-cid=\"root\"");
        assertThat(ssr.getBody()).as("projected text keeps a parent id")
                .contains("data-medley-id=\"" + PROJECTED_TEXT_ID + "\"");
    }

    @Test
    void parentActionPatchesProjectedContentWithASinglePatch() throws Exception {
        try (Ws ws = Ws.open(rest, port, "/panel")) {
            ws.send(BUMP);
            JsonNode patches = ws.receive();

            assertThat(patches).as("exactly one patch — the opaque boundary keeps the parent quiet")
                    .hasSize(1);
            assertThat(patches.get(0).get("op").asText()).isEqualTo("text");
            assertThat(patches.get(0).get("id").asText())
                    .as("projected content is diffed in the PARENT's id-space")
                    .isEqualTo(PROJECTED_TEXT_ID);
            assertThat(patches.get(0).get("value").asText()).isEqualTo("1");
        }
    }

    @Test
    void childActionLeavesProjectedContentAlone() throws Exception {
        try (Ws ws = Ws.open(rest, port, "/panel")) {
            ws.send(TICK);
            JsonNode patches = ws.receive();

            assertThat(patches).hasSize(1);
            assertThat(patches.get(0).get("id").asText())
                    .as("the child patches only its own subtree")
                    .isEqualTo(CHILD_CID + ".3.0");
            assertThat(patches.get(0).get("value").asText()).isEqualTo("1");
        }
    }

    @Test
    void aParentActionOnNonProjectedStateNeverTouchesTheChild() throws Exception {
        // The opaque-boundary half of the DoD, re-locked with a projection present: changing parent
        // state that is NOT inside the slot must patch only the parent's own node (root.2.0) and emit
        // nothing under the child — and the unchanged projection must not spuriously cascade either.
        try (Ws ws = Ws.open(rest, port, "/panel")) {
            ws.send(NUDGE);
            JsonNode patches = ws.receive();

            assertThat(patches).as("one patch — the child boundary is opaque and the projection is unchanged")
                    .hasSize(1);
            assertThat(patches.get(0).get("id").asText())
                    .as("only the parent's own non-projected node moved")
                    .isEqualTo("root.2.0");
            assertThat(patches.get(0).get("value").asText()).isEqualTo("1");
        }
    }

    @Test
    void resyncReturnsRootHtmlWithTheProjectionIntact() throws Exception {
        try (Ws ws = Ws.open(rest, port, "/panel")) {
            ws.send(BUMP);
            ws.receive();
            ws.send("{\"type\":\"resync\"}");
            JsonNode response = ws.receive();

            assertThat(response.get(0).get("op").asText()).isEqualTo("replace");
            String html = response.get(0).get("html").asText();
            assertThat(html).as("the resynced tree still carries the projection at its parent id")
                    .contains("data-medley-id=\"" + PROJECTED_TEXT_ID + "\"");
            assertThat(html).as("and the projected value the parent action produced").contains(">1<");
        }
    }

    /** Minimal WS client: SSR for the session cookie, then one socket with a blocking inbox. */
    private static final class Ws implements AutoCloseable {
        private final WebSocketSession session;
        private final BlockingQueue<String> inbound;
        private final ObjectMapper mapper = new ObjectMapper();

        private Ws(WebSocketSession session, BlockingQueue<String> inbound) {
            this.session = session;
            this.inbound = inbound;
        }

        static Ws open(TestRestTemplate rest, int port, String route) throws Exception {
            ResponseEntity<String> ssr = rest.getForEntity(route, String.class);
            String cookie = ssr.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];
            BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
            WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
            headers.add(HttpHeaders.COOKIE, cookie);
            WebSocketSession s = new StandardWebSocketClient()
                    .execute(new TextWebSocketHandler() {
                        @Override
                        protected void handleTextMessage(WebSocketSession ignored, TextMessage m) {
                            inbox.add(m.getPayload());
                        }
                    }, headers, URI.create("ws://localhost:" + port + "/medley/ws"))
                    .get(5, TimeUnit.SECONDS);
            return new Ws(s, inbox);
        }

        void send(String payload) throws Exception {
            session.sendMessage(new TextMessage(payload));
        }

        JsonNode receive() throws Exception {
            String raw = inbound.poll(5, TimeUnit.SECONDS);
            assertThat(raw).as("expected a response").isNotNull();
            JsonNode node = mapper.readTree(raw);
            assertThat(node.isArray()).as("response must be a patch array, was: " + raw).isTrue();
            return node;
        }

        @Override
        public void close() throws Exception {
            session.close();
        }
    }
}
