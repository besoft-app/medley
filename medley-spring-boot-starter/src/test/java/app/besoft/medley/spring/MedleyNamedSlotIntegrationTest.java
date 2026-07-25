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
 * End-to-end proof of named slots + fallback over a real Spring WebSocket — Stage 6, increment 6.3.
 *
 * <p>{@code /card} fills a card child's named {@code header} slot and its default body; {@code /card-plain}
 * leaves the header unfilled so it shows the child's fallback. Locks: named + default content routed to
 * the parent (parent cid); a parent action on named-slot content → exactly one patch in the parent's
 * id-space; a child action touches only the child; and the fallback renders at a child id.</p>
 */
@SpringBootTest(classes = TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MedleyNamedSlotIntegrationTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;

    private static final String CHILD_CID = "root.0::card";
    private static final String HEADER_TEXT_ID = "root.0.0.0";   // parent id of the projected header text
    private static final String RENAME = "{\"componentId\":\"root\",\"action\":\"rename\"}";
    private static final String MARK = "{\"componentId\":\"" + CHILD_CID + "\",\"action\":\"mark\"}";

    @Test
    void ssrEmbedsNamedAndDefaultSlotContentWithTheParentCid() {
        ResponseEntity<String> ssr = rest.getForEntity("/card", String.class);
        assertThat(ssr.getStatusCode().value()).isEqualTo(200);
        assertThat(ssr.getBody()).contains("data-medley-cid=\"" + CHILD_CID + "\"");
        // Attributes are asserted independently: VNode.VElement stores attrs via Map.copyOf, whose
        // iteration order is unspecified, so the emitted order of name/data-medley-cid is not stable
        // across JVMs — only their presence on the header slot element is contractual.
        String headerSlotTag = tagAt(ssr.getBody(), CHILD_CID + ".0.0");
        assertThat(headerSlotTag).as("the header slot is a real element at its child id").startsWith("<medley-slot ");
        assertThat(headerSlotTag).as("named slot").contains("name=\"header\"");
        assertThat(headerSlotTag).as("filled → routed to the parent").contains("data-medley-cid=\"root\"");
        assertThat(ssr.getBody()).as("the slot= routing directive was stripped from the projected node")
                .doesNotContain("slot=\"header\"");
        assertThat(ssr.getBody()).as("projected header text keeps a parent id")
                .contains("data-medley-id=\"" + HEADER_TEXT_ID + "\"");
        assertThat(ssr.getBody()).as("the projected header text").contains(">First<");
    }

    @Test
    void fallbackShowsWhenTheParentLeavesTheSlotUnfilled() {
        ResponseEntity<String> ssr = rest.getForEntity("/card-plain", String.class);
        assertThat(ssr.getStatusCode().value()).isEqualTo(200);
        assertThat(ssr.getBody()).as("the header slot, at its child id, renders its fallback and no parent cid")
                .contains("<medley-slot data-medley-id=\"" + CHILD_CID + ".0.0\" name=\"header\">Untitled</medley-slot>");
    }

    @Test
    void aParentActionChangingNamedSlotContentEmitsOneParentSpacePatch() throws Exception {
        try (Ws ws = Ws.open(rest, port, "/card")) {
            ws.send(RENAME);
            JsonNode patches = ws.receive();
            assertThat(patches).as("one patch — the opaque boundary keeps the parent quiet").hasSize(1);
            assertThat(patches.get(0).get("op").asText()).isEqualTo("text");
            assertThat(patches.get(0).get("id").asText())
                    .as("named-slot content is diffed in the parent's id-space").isEqualTo(HEADER_TEXT_ID);
            assertThat(patches.get(0).get("value").asText()).isEqualTo("Second");
        }
    }

    @Test
    void aChildActionTouchesOnlyTheChild() throws Exception {
        try (Ws ws = Ws.open(rest, port, "/card")) {
            ws.send(MARK);
            JsonNode patches = ws.receive();
            assertThat(patches).hasSize(1);
            assertThat(patches.get(0).get("id").asText()).isEqualTo(CHILD_CID + ".3.0");
            assertThat(patches.get(0).get("value").asText()).isEqualTo("1");
        }
    }

    /** The opening tag (from {@code '<'} to the matching {@code '>'}) of the element carrying
     *  {@code data-medley-id=id}, so attributes can be asserted independently of their emitted order
     *  ({@link app.besoft.medley.core.vnode.VNode.VElement} stores attrs via {@code Map.copyOf}, whose
     *  iteration order is unspecified). */
    private static String tagAt(String html, String id) {
        int idAt = html.indexOf("data-medley-id=\"" + id + "\"");
        assertThat(idAt).as("no element with id " + id).isGreaterThanOrEqualTo(0);
        return html.substring(html.lastIndexOf('<', idAt), html.indexOf('>', idAt) + 1);
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

        void send(String payload) throws Exception { session.sendMessage(new TextMessage(payload)); }

        JsonNode receive() throws Exception {
            String raw = inbound.poll(5, TimeUnit.SECONDS);
            assertThat(raw).as("expected a response").isNotNull();
            JsonNode node = mapper.readTree(raw);
            assertThat(node.isArray()).as("response must be a patch array, was: " + raw).isTrue();
            return node;
        }

        @Override public void close() throws Exception { session.close(); }
    }
}
