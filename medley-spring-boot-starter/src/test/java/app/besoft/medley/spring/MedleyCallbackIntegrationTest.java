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
 * Child→parent callbacks over a real Spring WebSocket (post-v0.4.0). At {@code /editor} a
 * {@code callback-parent} nests a {@code callback-editor} child and binds the child's {@code save}
 * output to its own {@code onEditorSave} action on the boundary ({@code @save="onEditorSave($event)"}).
 *
 * <p>The headline proof: an action addressed to the <em>child</em> instance (the child's {@code keep},
 * which fires the emitter) comes back as a patch on the <em>parent's</em> DOM node — the emit reached
 * the owner action in-process and its patch rode back in the same response, with no extra wire message
 * and no double emission.</p>
 */
@SpringBootTest(classes = TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MedleyCallbackIntegrationTest {

    private static final String CHILD_ID = "root.1::callback-editor";

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void childEmitDrivesTheBoundParentActionOverTheWire() throws Exception {
        ResponseEntity<String> ssr = rest.getForEntity("/editor", String.class);
        assertThat(ssr.getBody())
                .as("SSR mounts the editor child at the boundary")
                .contains("data-medley-cid=\"" + CHILD_ID + "\"");
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
            // Type into the child (its own action) — commits the draft to the child's rendered tree,
            // so the later `keep` produces no own patch. The patch here is the child's own text node.
            ws.sendMessage(new TextMessage(
                    "{\"componentId\":\"" + CHILD_ID + "\",\"action\":\"type\",\"args\":[\"hello\"]}"));
            JsonNode typed = readJson(inbound);
            assertThat(typed).hasSize(1);
            assertThat(typed.get(0).get("id").asText()).startsWith(CHILD_ID);
            assertThat(typed.get(0).get("value").asText()).isEqualTo("hello");

            // Click Save (the child's `keep` fires the @Output). The response is a single patch on the
            // PARENT's node (root.0.0) — the callback reached onEditorSave over the wire, no double emit.
            ws.sendMessage(new TextMessage(
                    "{\"componentId\":\"" + CHILD_ID + "\",\"action\":\"keep\"}"));
            JsonNode patches = readJson(inbound);
            assertThat(patches.isArray()).isTrue();
            assertThat(patches).as("only the parent's single text patch — no double emission").hasSize(1);
            assertThat(patches.get(0).get("op").asText()).isEqualTo("text");
            assertThat(patches.get(0).get("id").asText())
                    .as("the patch lands on the parent's node, not the child's — the child called up")
                    .isEqualTo("root.0.0");
            assertThat(patches.get(0).get("value").asText()).isEqualTo("hello");
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
