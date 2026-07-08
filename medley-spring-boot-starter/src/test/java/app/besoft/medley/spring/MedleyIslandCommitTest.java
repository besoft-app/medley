package app.besoft.medley.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.besoft.medley.core.component.ComponentInstance;
import app.besoft.medley.spring.fixtures.IslandHostComponent;
import app.besoft.medley.spring.fixtures.TickerIsland;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * End-to-end (handler-level) island commit: a commit dispatches to a {@code @IslandAction}, which
 * mutates the owning component; the re-render then pushes props back to the island host as a plain
 * attribute patch — and a normal component action never tears the island down (opacity).
 */
class MedleyIslandCommitTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private AnnotationConfigApplicationContext ctx;
    private MedleyWebSocketHandler handler;
    private TemplateRegistry templates;

    @BeforeEach
    void setUp() {
        ctx = new AnnotationConfigApplicationContext();
        ctx.register(TickerIsland.class);
        ctx.refresh();
        IslandRegistry islands = new IslandRegistry(ctx.getBeanFactory(), mapper);
        handler = new MedleyWebSocketHandler(mapper, new PatchEncoder(mapper), islands);
        templates = new TemplateRegistry("templates/medley/");
    }

    @AfterEach
    void tearDown() {
        ctx.close();
    }

    @Test
    void islandCommitMutatesOwnerAndPushesPropsToHost() throws Exception {
        MedleySession session = new MedleySession(templates);
        ComponentInstance instance = session.mount("root", new IslandHostComponent());
        instance.renderInitialHtml();
        WebSocketSession ws = wsWith(session);

        handler.handleTextMessage(ws, new TextMessage(
                "{\"type\":\"island-commit\",\"componentId\":\"root\",\"island\":\"ticker\","
                        + "\"action\":\"set\",\"payload\":{\"value\":7}}"));

        assertThat(((IslandHostComponent) instance.component()).getValue()).isEqualTo(7);

        JsonNode patches = mapper.readTree(onlySentPayload(ws));
        assertThat(patches.isArray()).isTrue();
        boolean propsPushed = false;
        for (JsonNode p : patches) {
            // The island's client DOM is opaque: it is never replaced/inserted/removed by a diff.
            assertThat(p.get("op").asText()).isNotIn("replace", "insert", "remove");
            if (p.get("op").asText().equals("attr")
                    && p.get("id").asText().equals("root.1")
                    && p.get("name").asText().equals("data-value")) {
                assertThat(p.get("value").asText()).isEqualTo("7");
                propsPushed = true;
            }
        }
        assertThat(propsPushed).as("props-push as an attr patch on the island host").isTrue();
    }

    @Test
    void normalComponentActionKeepsIslandOpaque() throws Exception {
        MedleySession session = new MedleySession(templates);
        ComponentInstance instance = session.mount("root", new IslandHostComponent());
        instance.renderInitialHtml();
        WebSocketSession ws = wsWith(session);

        handler.handleTextMessage(ws, new TextMessage(
                "{\"componentId\":\"root\",\"action\":\"bump\"}"));

        JsonNode patches = mapper.readTree(onlySentPayload(ws));
        for (JsonNode p : patches) {
            assertThat(p.get("op").asText())
                    .as("an island present in the tree must not cause teardown patches")
                    .isNotIn("replace", "insert", "remove");
        }
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
        return sent.getValue().getPayload();
    }
}
