package app.besoft.medley.spring;

import app.besoft.medley.core.component.ComponentInstance;
import app.besoft.medley.core.diff.Patch;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Handles the Medley WebSocket channel.
 *
 * <p>Incoming message: {@code {"componentId": "...", "action": "...", "args": [...]}}.
 * The handler looks up the live component in the HTTP-session-bound {@link MedleySession},
 * invokes the named {@code @Action} (whitelisted server-side), and writes back the resulting
 * patch list as JSON.</p>
 *
 * <p>Per-session ordering: messages on a single WebSocket session are delivered sequentially
 * by the container, and we synchronize on the session, so the render loop stays deterministic.</p>
 */
public class MedleyWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(MedleyWebSocketHandler.class);

    private final ObjectMapper mapper;
    private final PatchEncoder encoder;

    public MedleyWebSocketHandler(ObjectMapper mapper, PatchEncoder encoder) {
        this.mapper = mapper;
        this.encoder = encoder;
    }

    @Override
    protected void handleTextMessage(WebSocketSession wsSession, TextMessage message) throws Exception {
        MedleySession medley = medleySession(wsSession);
        if (medley == null) {
            sendError(wsSession, "No Medley session bound to this connection");
            return;
        }

        JsonNode msg = mapper.readTree(message.getPayload());
        String componentId = msg.path("componentId").asText(null);
        String action = msg.path("action").asText(null);
        if (componentId == null || action == null) {
            sendError(wsSession, "Message must contain componentId and action");
            return;
        }

        Object[] args = parseArgs(msg.path("args"));

        synchronized (wsSession) {
            ComponentInstance instance = medley.get(componentId);
            if (instance == null) {
                // Stale client (e.g. after server restart). Tell it to reload for a fresh SSR.
                wsSession.sendMessage(new TextMessage("{\"op\":\"reload\"}"));
                return;
            }
            List<Patch> patches;
            try {
                patches = instance.invokeAction(action, args);
            } catch (RuntimeException e) {
                // Unknown/failed action: log server-side (the client only gets a generic message)
                // and keep the socket open. Only invokeAction is guarded, so a serialization/
                // transport failure below is not misreported as an action failure.
                log.warn("Medley action '{}' on component '{}' failed", action, componentId, e);
                sendError(wsSession, "Action '" + action + "' failed");
                return;
            }
            wsSession.sendMessage(new TextMessage(encoder.encode(patches)));
        }
    }

    private Object[] parseArgs(JsonNode argsNode) {
        if (argsNode == null || !argsNode.isArray() || argsNode.isEmpty()) {
            return new Object[0];
        }
        Object[] args = new Object[argsNode.size()];
        for (int i = 0; i < argsNode.size(); i++) {
            JsonNode a = argsNode.get(i);
            if (a.isInt()) args[i] = a.asInt();
            else if (a.isBoolean()) args[i] = a.asBoolean();
            else if (a.isDouble()) args[i] = a.asDouble();
            else args[i] = a.asText();
        }
        return args;
    }

    private MedleySession medleySession(WebSocketSession wsSession) {
        Map<String, Object> attrs = wsSession.getAttributes();
        Object s = attrs.get(MedleySession.class.getName());
        return (s instanceof MedleySession ss) ? ss : null;
    }

    private void sendError(WebSocketSession wsSession, String message) throws Exception {
        ObjectNode n = mapper.createObjectNode();
        n.put("op", "error");
        n.put("message", message);
        wsSession.sendMessage(new TextMessage(mapper.writeValueAsString(n)));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        // Session-scoped state is released when the HTTP session expires, not on socket close,
        // so a transient disconnect/reconnect keeps component state intact.
    }
}
