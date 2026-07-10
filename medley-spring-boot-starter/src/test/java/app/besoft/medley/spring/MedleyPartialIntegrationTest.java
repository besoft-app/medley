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
 * End-to-end proof of template partials over SSR + a real Spring WebSocket (Stage 4, increment 4).
 *
 * <p>The routed template embeds {@code <medley-partial name="field" onChange="setName">}. SSR must
 * expand the fragment inline and rewrite its {@code @input="onChange($value)"} to the owner action
 * {@code setName}. A steady-state value change routed through the partial then patches only the echo
 * label — a single patch — proving composition preserves the minimal-diff guarantee. componentId
 * stays {@code "root"}: partials add no wire addressing.</p>
 */
@SpringBootTest(classes = TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MedleyPartialIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void partialExpandsInSsrAndRoutesEventToOwnerWithMinimalDiff() throws Exception {
        ResponseEntity<String> ssr = rest.getForEntity("/partials", String.class);
        assertThat(ssr.getStatusCode().value()).isEqualTo(200);
        String html = ssr.getBody();
        assertThat(html).as("fragment expanded inline with its param").contains("Imię");
        assertThat(html).as("event pass-through rewritten to the owner action")
                .contains("data-medley-on-input=\"setName($value)\"");
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
            // Warm up "" -> "ab" so the echo text node materializes.
            ws.sendMessage(new TextMessage(
                    "{\"componentId\":\"root\",\"action\":\"setName\",\"args\":[\"ab\"]}"));
            assertThat(inbound.poll(5, TimeUnit.SECONDS)).as("warm-up").isNotNull();

            // Steady state "ab" -> "cd": a single text patch on the echo label.
            ws.sendMessage(new TextMessage(
                    "{\"componentId\":\"root\",\"action\":\"setName\",\"args\":[\"cd\"]}"));
            String steady = inbound.poll(5, TimeUnit.SECONDS);
            assertThat(steady).as("steady-state response").isNotNull();

            JsonNode patches = mapper.readTree(steady);
            assertThat(patches.isArray()).isTrue();
            assertThat(patches).hasSize(1);
            assertThat(patches.get(0).get("op").asText()).isEqualTo("text");
            assertThat(patches.get(0).get("value").asText()).isEqualTo("cd");
        } finally {
            ws.close();
        }
    }
}
