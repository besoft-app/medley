package app.besoft.medley.spring;

import app.besoft.medley.core.diff.Patch;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Encodes patches to the compact JSON wire format consumed by medley.js.
 *
 * <p>Each patch becomes {@code {"op": "...", ...}}. Kept hand-rolled (rather than relying on
 * record auto-serialization) so the wire format stays explicit and stable, independent of
 * Java field names.</p>
 */
public class PatchEncoder {

    private final ObjectMapper mapper;

    public PatchEncoder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String encode(List<Patch> patches) throws JsonProcessingException {
        ArrayNode arr = mapper.createArrayNode();
        for (Patch p : patches) {
            arr.add(toNode(p));
        }
        return mapper.writeValueAsString(arr);
    }

    private ObjectNode toNode(Patch p) {
        ObjectNode n = mapper.createObjectNode();
        switch (p) {
            case Patch.SetText s -> {
                n.put("op", "text"); n.put("id", s.id()); n.put("value", s.value());
            }
            case Patch.SetAttr s -> {
                n.put("op", "attr"); n.put("id", s.id()); n.put("name", s.name()); n.put("value", s.value());
            }
            case Patch.RemoveAttr s -> {
                n.put("op", "removeAttr"); n.put("id", s.id()); n.put("name", s.name());
            }
            case Patch.SetEvent s -> {
                n.put("op", "event"); n.put("id", s.id()); n.put("event", s.event()); n.put("action", s.action());
            }
            case Patch.RemoveEvent s -> {
                n.put("op", "removeEvent"); n.put("id", s.id()); n.put("event", s.event());
            }
            case Patch.Replace s -> {
                n.put("op", "replace"); n.put("id", s.id()); n.put("html", s.html());
            }
            case Patch.Insert s -> {
                n.put("op", "insert"); n.put("id", s.id()); n.put("parentId", s.parentId());
                n.put("index", s.index()); n.put("html", s.html());
            }
            case Patch.Remove s -> {
                n.put("op", "remove"); n.put("id", s.id());
            }
        }
        return n;
    }
}
