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
 * Keyed {@code *for} end-to-end over a real Spring WebSocket: adding a row yields exactly one
 * {@code insert} (carrying the keyed row's HTML), removing a row yields exactly one {@code remove}
 * — the minimal-patch guarantee the whitespace-tight {@code <tbody>} preserves.
 */
@SpringBootTest(classes = TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MedleyKeyedListIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void addAndRemoveKeyedRowsEmitMinimalPatches() throws Exception {
        ResponseEntity<String> ssr = rest.getForEntity("/table", String.class);
        assertThat(ssr.getStatusCode().value()).isEqualTo(200);
        assertThat(ssr.getBody()).contains("data-medley-key=\"1\""); // rows rendered with keys
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
            // Add a row -> exactly one insert appended under <tbody> (root.0.0), carrying the new
            // keyed row. Ids: div=root, table=root.0, tbody=root.0.0, tr[key]=root.0.0.0[key].
            ws.sendMessage(new TextMessage("{\"componentId\":\"root\",\"action\":\"addRow\"}"));
            JsonNode added = mapper.readTree(poll(inbound));
            assertThat(added).hasSize(1);
            JsonNode insert = added.get(0);
            assertThat(insert.get("op").asText()).isEqualTo("insert");
            assertThat(insert.get("parentId").asText()).isEqualTo("root.0.0");
            assertThat(insert.get("index").asInt()).isEqualTo(2); // appended after the two seed rows
            assertThat(insert.get("html").asText()).contains("data-medley-key=\"3\"");

            // Remove the first row -> exactly one remove of that key, no sibling churn.
            ws.sendMessage(new TextMessage("{\"componentId\":\"root\",\"action\":\"removeFirst\"}"));
            JsonNode removed = mapper.readTree(poll(inbound));
            assertThat(removed).hasSize(1);
            assertThat(removed.get(0).get("op").asText()).isEqualTo("remove");
            assertThat(removed.get(0).get("id").asText()).isEqualTo("root.0.0.0[1]");
        } finally {
            ws.close();
        }
    }

    private String poll(BlockingQueue<String> q) throws InterruptedException {
        String msg = q.poll(5, TimeUnit.SECONDS);
        assertThat(msg).as("expected a WS response").isNotNull();
        return msg;
    }
}
