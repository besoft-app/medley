package app.besoft.medley.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.besoft.medley.spring.fixtures.TestCounterComponent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Exercises the WebSocket handler's contract against a mock socket — fast and deterministic.
 * The live end-to-end proof over a real socket lives in
 * {@link MedleyWebSocketLoopIntegrationTest}.
 */
class MedleyWebSocketHandlerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private MedleyWebSocketHandler handler;
    private TemplateRegistry templates;

    private static final String INCREMENT = "{\"componentId\":\"root\",\"action\":\"increment\"}";

    @BeforeEach
    void setUp() {
        handler = new MedleyWebSocketHandler(mapper, new PatchEncoder(mapper));
        templates = new TemplateRegistry("templates/medley/");
    }

    @Test
    void steadyStateIncrementEmitsExactlyOneTextPatch() throws Exception {
        MedleySession session = new MedleySession(templates);
        // SSR would have rendered the initial tree; mirror that so the first diff has a baseline.
        session.mount("root", new TestCounterComponent()).renderInitialHtml();
        WebSocketSession ws = wsWith(session);

        handler.handleTextMessage(ws, new TextMessage(INCREMENT)); // 0 -> 1 (placeholders materialize)
        handler.handleTextMessage(ws, new TextMessage(INCREMENT)); // 1 -> 2 (steady state)

        ArgumentCaptor<TextMessage> sent = ArgumentCaptor.forClass(TextMessage.class);
        verify(ws, times(2)).sendMessage(sent.capture());

        JsonNode steady = mapper.readTree(sent.getAllValues().get(1).getPayload());
        assertThat(steady.isArray()).isTrue();
        assertThat(steady).hasSize(1);
        assertThat(steady.get(0).get("op").asText()).isEqualTo("text");
        assertThat(steady.get(0).get("id").asText()).isEqualTo("root.3.2");
        assertThat(steady.get(0).get("value").asText()).isEqualTo("2");
    }

    @Test
    void unknownComponentTellsClientToReload() throws Exception {
        WebSocketSession ws = wsWith(new MedleySession(templates)); // nothing mounted under "root"

        handler.handleTextMessage(ws, new TextMessage(INCREMENT));

        assertThat(onlySentPayload(ws)).isEqualTo("{\"op\":\"reload\"}");
    }

    @Test
    void missingMedleySessionYieldsError() throws Exception {
        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.getAttributes()).thenReturn(new HashMap<>()); // no MedleySession bound

        handler.handleTextMessage(ws, new TextMessage(INCREMENT));

        assertThat(mapper.readTree(onlySentPayload(ws)).get("op").asText()).isEqualTo("error");
    }

    @Test
    void unknownActionYieldsErrorWithoutClosing() throws Exception {
        MedleySession session = new MedleySession(templates);
        session.mount("root", new TestCounterComponent()).renderInitialHtml();
        WebSocketSession ws = wsWith(session);

        handler.handleTextMessage(ws, new TextMessage("{\"componentId\":\"root\",\"action\":\"bogus\"}"));

        assertThat(mapper.readTree(onlySentPayload(ws)).get("op").asText()).isEqualTo("error");
    }

    private WebSocketSession wsWith(MedleySession session) {
        WebSocketSession ws = mock(WebSocketSession.class);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(MedleySession.class.getName(), session);
        when(ws.getAttributes()).thenReturn(attrs);
        return ws;
    }

    private String onlySentPayload(WebSocketSession ws) throws Exception {
        ArgumentCaptor<TextMessage> sent = ArgumentCaptor.forClass(TextMessage.class);
        verify(ws).sendMessage(sent.capture());
        List<TextMessage> all = sent.getAllValues();
        return all.get(all.size() - 1).getPayload();
    }
}
