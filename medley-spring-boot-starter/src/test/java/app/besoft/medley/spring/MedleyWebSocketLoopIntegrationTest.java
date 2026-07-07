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
 * End-to-end proof over a real Spring WebSocket — the Stage-2 equivalent of the dev-server
 * proof: a steady-state increment produces exactly one text patch
 * {@code {"op":"text","id":"root.3.2","value":"2"}}.
 *
 * <p>Flow: SSR {@code GET /counter} creates the HTTP session + mounted component tree; the WS
 * handshake carries that session's cookie so the handler dispatches to the same instance.</p>
 */
@SpringBootTest(classes = TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MedleyWebSocketLoopIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private final ObjectMapper mapper = new ObjectMapper();
    private static final String INCREMENT = "{\"componentId\":\"root\",\"action\":\"increment\"}";

    @Test
    void steadyStateIncrementEmitsExactlyOneTextPatchOverTheWire() throws Exception {
        // 1. SSR — establishes the HTTP session and the server-side component tree.
        ResponseEntity<String> ssr = rest.getForEntity("/counter", String.class);
        assertThat(ssr.getStatusCode().value()).isEqualTo(200);
        String setCookie = ssr.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).as("SSR must start a session").contains("JSESSIONID");
        String sessionCookie = setCookie.split(";", 2)[0]; // "JSESSIONID=..."

        // 2. Open the WebSocket carrying the same session cookie so the handshake binds it.
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
            // 3. Warm up 0 -> 1: two *if placeholders materialize, so >1 patch is expected here.
            ws.sendMessage(new TextMessage(INCREMENT));
            assertThat(inbound.poll(5, TimeUnit.SECONDS)).as("warm-up response").isNotNull();

            // 4. Steady state 1 -> 2: exactly one text patch.
            ws.sendMessage(new TextMessage(INCREMENT));
            String steady = inbound.poll(5, TimeUnit.SECONDS);
            assertThat(steady).as("steady-state response").isNotNull();

            JsonNode patches = mapper.readTree(steady);
            assertThat(patches.isArray()).isTrue();
            assertThat(patches).hasSize(1);
            assertThat(patches.get(0).get("op").asText()).isEqualTo("text");
            assertThat(patches.get(0).get("id").asText()).isEqualTo("root.3.2");
            assertThat(patches.get(0).get("value").asText()).isEqualTo("2");
        } finally {
            ws.close();
        }
    }
}
