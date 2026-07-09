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
 * End-to-end proof that form validation is enforced on the server (Stage 4, increment 3).
 *
 * <p>A raw client submits directly over the socket — bypassing any client-side hint. An invalid
 * submit renders the error and does <em>not</em> commit; a subsequent valid submit commits exactly
 * once (the {@code submitted} counter reaches "1", never "2"), proving the earlier invalid submit
 * was rejected server-side. No wire-protocol change — errors are ordinary re-render patches.</p>
 */
@SpringBootTest(classes = TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MedleyFormValidationIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void invalidSubmitIsRejectedServerSideAndValidSubmitCommitsOnce() throws Exception {
        ResponseEntity<String> ssr = rest.getForEntity("/signup", String.class);
        assertThat(ssr.getStatusCode().value()).isEqualTo(200);
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
            // 1. Invalid submit (name is ""): the error renders, nothing commits.
            ws.sendMessage(new TextMessage(
                    "{\"componentId\":\"root\",\"action\":\"submit\",\"args\":[]}"));
            String invalid = inbound.poll(5, TimeUnit.SECONDS);
            assertThat(invalid).as("invalid submit response").isNotNull();
            assertThat(invalid).as("error message is rendered").contains("at least 2 characters");

            // 2. Provide a valid name (uncontrolled input -> no echo patch expected).
            ws.sendMessage(new TextMessage(
                    "{\"componentId\":\"root\",\"action\":\"setName\",\"args\":[\"Ada\"]}"));
            inbound.poll(5, TimeUnit.SECONDS); // drain (may be an empty patch array)

            // 3. Valid submit: commits exactly once -> submitted counter becomes "1", not "2".
            ws.sendMessage(new TextMessage(
                    "{\"componentId\":\"root\",\"action\":\"submit\",\"args\":[]}"));
            String valid = inbound.poll(5, TimeUnit.SECONDS);
            assertThat(valid).as("valid submit response").isNotNull();

            JsonNode patches = mapper.readTree(valid);
            assertThat(patches.isArray()).isTrue();
            boolean committedToOne = false;
            for (JsonNode p : patches) {
                if ("text".equals(p.path("op").asText())) {
                    assertThat(p.get("value").asText())
                            .as("submitted must reach 1, never 2 (no double-commit)")
                            .isEqualTo("1");
                    committedToOne = true;
                }
            }
            assertThat(committedToOne).as("valid submit committed (submitted text patch present)").isTrue();
        } finally {
            ws.close();
        }
    }
}
