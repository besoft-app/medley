package app.besoft.medley.spring;

import static org.assertj.core.api.Assertions.assertThat;

import app.besoft.medley.core.diff.Patch;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

/** The wire format medley.js consumes is stable and independent of Java field names. */
class PatchEncoderTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final PatchEncoder encoder = new PatchEncoder(mapper);

    @Test
    void encodesTextPatchAsExpectedShape() throws Exception {
        JsonNode arr = mapper.readTree(encoder.encode(List.of(new Patch.SetText("root.3.2", "2"))));

        assertThat(arr.isArray()).isTrue();
        assertThat(arr).hasSize(1);
        assertThat(arr.get(0).get("op").asText()).isEqualTo("text");
        assertThat(arr.get(0).get("id").asText()).isEqualTo("root.3.2");
        assertThat(arr.get(0).get("value").asText()).isEqualTo("2");
    }

    @Test
    void encodesInsertWithParentAndIndex() throws Exception {
        JsonNode arr = mapper.readTree(
                encoder.encode(List.of(new Patch.Insert("root.1", "root", 1, "<button>+</button>"))));

        JsonNode n = arr.get(0);
        assertThat(n.get("op").asText()).isEqualTo("insert");
        assertThat(n.get("id").asText()).isEqualTo("root.1");
        assertThat(n.get("parentId").asText()).isEqualTo("root");
        assertThat(n.get("index").asInt()).isEqualTo(1);
        assertThat(n.get("html").asText()).isEqualTo("<button>+</button>");
    }
}
