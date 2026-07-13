package app.besoft.medley.spring;

import app.besoft.medley.core.component.ComponentInstance;
import app.besoft.medley.core.diff.Patch;
import app.besoft.medley.core.diff.PatchMerge;

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
 * <p>Two inbound message shapes, disambiguated by a top-level {@code type}:
 * <ul>
 *   <li>a component action (no {@code type}):
 *       {@code {"componentId":"...","action":"...","args":[...]}} — invokes the named
 *       {@code @Action} on the live component;</li>
 *   <li>an island commit: {@code {"type":"island-commit","componentId":"...","island":"...",
 *       "action":"...","payload":{...}}} — dispatches to a {@code @IslandAction}, which mutates
 *       the owning component.</li>
 * </ul>
 * Both then re-render the owning component and write back the resulting patch array as JSON.</p>
 *
 * <p>Per-session ordering: messages on a single WebSocket session are delivered sequentially
 * by the container, and we synchronize on the session, so the render loop stays deterministic.</p>
 */
public class MedleyWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(MedleyWebSocketHandler.class);

    private final ObjectMapper mapper;
    private final PatchEncoder encoder;
    private final IslandRegistry islands;
    private final int maxMessageLength;
    private final MedleyMetrics metrics;

    /** No inbound size cap — for PoC/tests that don't wire {@link MedleyProperties}. */
    public MedleyWebSocketHandler(ObjectMapper mapper, PatchEncoder encoder, IslandRegistry islands) {
        this(mapper, encoder, islands, 0, MedleyMetrics.NOOP);
    }

    public MedleyWebSocketHandler(ObjectMapper mapper, PatchEncoder encoder, IslandRegistry islands,
                                  int maxMessageLength) {
        this(mapper, encoder, islands, maxMessageLength, MedleyMetrics.NOOP);
    }

    public MedleyWebSocketHandler(ObjectMapper mapper, PatchEncoder encoder, IslandRegistry islands,
                                  int maxMessageLength, MedleyMetrics metrics) {
        this.mapper = mapper;
        this.encoder = encoder;
        this.islands = islands;
        this.maxMessageLength = maxMessageLength;
        this.metrics = metrics;
    }

    @Override
    protected void handleTextMessage(WebSocketSession wsSession, TextMessage message) throws Exception {
        // Inbound hardening (5c): reject an oversized frame before parsing/processing it.
        if (maxMessageLength > 0 && message.getPayloadLength() > maxMessageLength) {
            log.warn("Rejected oversized Medley message: {} > {}", message.getPayloadLength(), maxMessageLength);
            sendError(wsSession, "Message too large");
            return;
        }

        MedleySession medley = medleySession(wsSession);
        if (medley == null) {
            sendError(wsSession, "No Medley session bound to this connection");
            return;
        }

        JsonNode msg = mapper.readTree(message.getPayload());
        String type = msg.path("type").asText(null);
        if ("island-commit".equals(type)) {
            handleIslandCommit(wsSession, medley, msg);
            return;
        }
        if ("resync".equals(type)) {
            handleResync(wsSession, medley);
            return;
        }

        String componentId = msg.path("componentId").asText(null);
        String action = msg.path("action").asText(null);
        if (componentId == null || action == null) {
            sendError(wsSession, "Message must contain componentId and action");
            return;
        }

        Object[] args = parseArgs(msg.path("args"));

        long start = System.nanoTime();
        synchronized (wsSession) {
            ComponentInstance instance = medley.get(componentId);
            if (instance == null) {
                // Stale client (e.g. after server restart). Tell it to reload for a fresh SSR.
                wsSession.sendMessage(new TextMessage("{\"op\":\"reload\"}"));
                metrics.recordMessage("action", 0, System.nanoTime() - start, true);
                return;
            }
            List<Patch> patches;
            try {
                medley.resetRenderCycle();
                patches = instance.invokeAction(action, args);
                // Props-down cascade (4b.3a): append patches from children whose bound params changed
                // during this render. The opaque boundary keeps invokeAction from also emitting them.
                patches = PatchMerge.mergeWithSuppression(patches, medley.drainCascade());
                // Eviction (4b.3b): a child whose boundary was structurally removed this render is freed.
                medley.evictByPatches(patches);
            } catch (RuntimeException e) {
                // Unknown/failed action: log server-side (the client only gets a generic message)
                // and keep the socket open. Only invokeAction is guarded, so a serialization/
                // transport failure below is not misreported as an action failure.
                log.warn("Medley action '{}' on component '{}' failed", action, componentId, e);
                sendError(wsSession, "Action '" + action + "' failed");
                metrics.recordMessage("action", 0, System.nanoTime() - start, false);
                return;
            }
            wsSession.sendMessage(new TextMessage(encoder.encode(patches)));
            metrics.recordMessage("action", patches.size(), System.nanoTime() - start, true);
        }
    }

    /**
     * Dispatch an island commit to a {@code @IslandAction} on the owning component, then push the
     * component's re-render (a changed bound prop surfaces as a host-attribute patch on the island).
     */
    private void handleIslandCommit(WebSocketSession wsSession, MedleySession medley, JsonNode msg)
            throws Exception {
        String componentId = msg.path("componentId").asText(null);
        String island = msg.path("island").asText(null);
        String action = msg.path("action").asText(null);
        // msg."id" (the island host's data-medley-id) is sent by the client and reserved for
        // future multi-instance targeting; dispatch today routes by componentId + island name.
        if (componentId == null || island == null || action == null) {
            sendError(wsSession, "island-commit must contain componentId, island and action");
            return;
        }
        JsonNode payload = msg.get("payload");

        long start = System.nanoTime();
        synchronized (wsSession) {
            ComponentInstance instance = medley.get(componentId);
            if (instance == null) {
                wsSession.sendMessage(new TextMessage("{\"op\":\"reload\"}"));
                metrics.recordMessage("island-commit", 0, System.nanoTime() - start, true);
                return;
            }
            List<Patch> patches;
            try {
                medley.resetRenderCycle();
                islands.invoke(island, action, instance.component(), payload);
                patches = instance.renderToPatches();
                patches = PatchMerge.mergeWithSuppression(patches, medley.drainCascade());
                medley.evictByPatches(patches);
            } catch (RuntimeException e) {
                log.warn("Medley island commit '{}.{}' on component '{}' failed",
                        island, action, componentId, e);
                sendError(wsSession, "Island commit '" + island + "." + action + "' failed");
                metrics.recordMessage("island-commit", 0, System.nanoTime() - start, false);
                return;
            }
            wsSession.sendMessage(new TextMessage(encoder.encode(patches)));
            metrics.recordMessage("island-commit", patches.size(), System.nanoTime() - start, true);
        }
    }

    /**
     * Reconnect resync (Stage 4, increment 6b): re-render the root fresh and reply with a single
     * {@code replace} of the root subtree, so a client that reconnected after a drop (and may have
     * missed patches) is brought back into a guaranteed-consistent state. A missing root (stale
     * session after a restart) falls back to a full page reload.
     */
    private void handleResync(WebSocketSession wsSession, MedleySession medley) throws Exception {
        long start = System.nanoTime();
        synchronized (wsSession) {
            ComponentInstance root = medley.get("root");
            if (root == null) {
                wsSession.sendMessage(new TextMessage("{\"op\":\"reload\"}"));
                metrics.recordMessage("resync", 0, System.nanoTime() - start, true);
                return;
            }
            String html;
            try {
                medley.resetRenderCycle();
                html = root.resyncHtml();
            } catch (RuntimeException e) {
                log.warn("Medley resync failed", e);
                sendError(wsSession, "Resync failed");
                metrics.recordMessage("resync", 0, System.nanoTime() - start, false);
                return;
            }
            List<Patch> patches = List.of(new Patch.Replace(root.id(), html));
            wsSession.sendMessage(new TextMessage(encoder.encode(patches)));
            metrics.recordMessage("resync", patches.size(), System.nanoTime() - start, true);
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
