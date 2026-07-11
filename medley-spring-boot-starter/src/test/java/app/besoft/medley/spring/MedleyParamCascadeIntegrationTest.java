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
 * End-to-end proof of the props-down cascade over a real Spring WebSocket — 4b.3a.
 *
 * <p>A routed {@code /cascade} host passes its own {@code @State} down to an {@code echo-param} child
 * as a bound {@code :label}. A parent action that changes the host state must re-inject the child, fire
 * its {@code onParamChange}, and deliver the child's re-render — as <b>exactly one</b> text patch
 * addressed inside the child (the host does not render the label itself, and the opaque boundary keeps
 * the host diff from emitting the child subtree, so nothing else appears).</p>
 */
@SpringBootTest(classes = TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MedleyParamCascadeIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private final ObjectMapper mapper = new ObjectMapper();

    private static final String CHILD_CID = "root.1::echo-param";
    private static final String RELABEL = "{\"componentId\":\"root\",\"action\":\"relabel\"}";

    @Test
    void parentStateChangeCascadesASingleChildPatch() throws Exception {
        ResponseEntity<String> ssr = rest.getForEntity("/cascade", String.class);
        assertThat(ssr.getStatusCode().value()).isEqualTo(200);
        assertThat(ssr.getBody()).contains("data-medley-cid=\"" + CHILD_CID + "\"");
        assertThat(ssr.getBody()).as("child echoes the initial :label").contains(">a<");

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
            ws.sendMessage(new TextMessage(RELABEL));
            String raw = inbound.poll(5, TimeUnit.SECONDS);
            assertThat(raw).as("expected a patch response").isNotNull();

            JsonNode patches = mapper.readTree(raw);
            assertThat(patches.isArray()).as("response must be a patch array, was: " + raw).isTrue();
            assertThat(patches).as("exactly one cascaded child patch, no parent leakage").hasSize(1);
            assertThat(patches.get(0).get("op").asText()).isEqualTo("text");
            assertThat(patches.get(0).get("id").asText())
                    .as("patch is addressed inside the child instance")
                    .startsWith(CHILD_CID);
            assertThat(patches.get(0).get("value").asText()).isEqualTo("b");
        } finally {
            ws.close();
        }
    }
}
