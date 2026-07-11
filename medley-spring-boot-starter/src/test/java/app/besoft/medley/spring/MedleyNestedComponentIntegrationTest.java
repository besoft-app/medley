package app.besoft.medley.spring;

import static org.assertj.core.api.Assertions.assertThat;

import app.besoft.medley.spring.fixtures.TestApplication;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

/**
 * SSR proof of nested server components — 4b.1 (static child render + {@code @Param} down).
 *
 * <p>A routed host nests {@code counter-badge} twice via {@code <medley-component>}. SSR must mount a
 * fresh child per placement, inject each {@code start} {@code @Param} (bound {@code :start="n"} and
 * static {@code start="9"}), render the child template at the child instance id {@code hostId::name},
 * and mark each boundary host with {@code data-medley-cid}. The two placements get non-colliding ids.
 * (Action routing over the WebSocket is 4b.2.)</p>
 */
@SpringBootTest(classes = TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MedleyNestedComponentIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void nestedChildrenRenderWithParamsAndDistinctIds() {
        ResponseEntity<String> ssr = rest.getForEntity("/nested", String.class);
        assertThat(ssr.getStatusCode().value()).isEqualTo(200);
        String html = ssr.getBody();

        // Two boundary hosts with non-colliding child instance ids.
        assertThat(html).contains("data-medley-cid=\"root.0::counter-badge\"");
        assertThat(html).contains("data-medley-cid=\"root.1::counter-badge\"");
        // Each child rendered its own injected @Param: bound :start="n" (=5) and static start="9".
        assertThat(html).contains("count: 5");
        assertThat(html).contains("count: 9");
        // The child tree is rooted at the child instance id.
        assertThat(html).contains("data-medley-id=\"root.0::counter-badge\"");
    }
}
