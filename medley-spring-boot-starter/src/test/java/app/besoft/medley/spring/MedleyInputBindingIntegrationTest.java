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
 * End-to-end proof of input-value binding over a real Spring WebSocket (Stage 4, increment 2).
 *
 * <p>The client sends an {@code args} array carrying the input's value; the server coerces it to
 * the {@code @Action}'s declared parameter type, mutates {@code @State}, re-renders and diffs.
 * Because the input is uncontrolled (no {@code :value} echo), a steady-state value change patches
 * only the echo label — exactly one patch — the same minimal-diff guarantee the counter test pins.
 * A checkbox carries {@code $checked} as a real JSON boolean to a {@code boolean} param.</p>
 */
@SpringBootTest(classes = TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MedleyInputBindingIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private final ObjectMapper mapper = new ObjectMapper();

    private WebSocketSession openSocketForFreshSession(BlockingQueue<String> inbound) throws Exception {
        ResponseEntity<String> ssr = rest.getForEntity("/search", String.class);
        assertThat(ssr.getStatusCode().value()).isEqualTo(200);
        String setCookie = ssr.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).as("SSR must start a session").contains("JSESSIONID");
        String sessionCookie = setCookie.split(";", 2)[0];

        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add(HttpHeaders.COOKIE, sessionCookie);
        return new StandardWebSocketClient()
                .execute(new TextWebSocketHandler() {
                    @Override
                    protected void handleTextMessage(WebSocketSession s, TextMessage m) {
                        inbound.add(m.getPayload());
                    }
                }, headers, URI.create("ws://localhost:" + port + "/medley/ws"))
                .get(5, TimeUnit.SECONDS);
    }

    @Test
    void steadyStateValueBindingEmitsExactlyOneTextPatch() throws Exception {
        BlockingQueue<String> inbound = new LinkedBlockingQueue<>();
        WebSocketSession ws = openSocketForFreshSession(inbound);
        try {
            // Warm up "" -> "ab" so the echo text node is materialized.
            ws.sendMessage(new TextMessage(
                    "{\"componentId\":\"root\",\"action\":\"setQuery\",\"args\":[\"ab\"]}"));
            assertThat(inbound.poll(5, TimeUnit.SECONDS)).as("warm-up response").isNotNull();

            // Steady state "ab" -> "cd": exactly one text patch on the label, none on the input.
            ws.sendMessage(new TextMessage(
                    "{\"componentId\":\"root\",\"action\":\"setQuery\",\"args\":[\"cd\"]}"));
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

    @Test
    void checkedBooleanBindsToBooleanParam() throws Exception {
        BlockingQueue<String> inbound = new LinkedBlockingQueue<>();
        WebSocketSession ws = openSocketForFreshSession(inbound);
        try {
            // $checked arrives as a real JSON boolean; the active label flips false -> true.
            ws.sendMessage(new TextMessage(
                    "{\"componentId\":\"root\",\"action\":\"setActive\",\"args\":[true]}"));
            String resp = inbound.poll(5, TimeUnit.SECONDS);
            assertThat(resp).as("setActive response").isNotNull();

            JsonNode patches = mapper.readTree(resp);
            assertThat(patches.isArray()).isTrue();
            assertThat(patches).hasSize(1);
            assertThat(patches.get(0).get("op").asText()).isEqualTo("text");
            assertThat(patches.get(0).get("value").asText()).isEqualTo("true");
        } finally {
            ws.close();
        }
    }
}
