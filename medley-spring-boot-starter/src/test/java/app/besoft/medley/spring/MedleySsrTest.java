package app.besoft.medley.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.besoft.medley.spring.fixtures.TestApplication;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * First-request SSR + auto-config wiring, plus the regression guard that exact-path routing no
 * longer shadows the framework's own {@code /medley/medley.js} asset.
 */
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class MedleySsrTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ApplicationContext ctx;

    @Autowired
    MedleyProperties properties;

    @Test
    void autoConfigurationRegistersTheCoreBeans() {
        assertThat(ctx.getBean(RouteRegistry.class)).isNotNull();
        assertThat(ctx.getBean(TemplateRegistry.class)).isNotNull();
        assertThat(ctx.getBean(PatchEncoder.class)).isNotNull();
        assertThat(ctx.getBean(MedleyController.class)).isNotNull();
        assertThat(ctx.getBean(MedleyWebSocketHandler.class)).isNotNull();
        assertThat(ctx.getBean(MedleyHandshakeInterceptor.class)).isNotNull();
        assertThat(ctx.getBean(MedleyWebSocketConfig.class)).isNotNull();
        assertThat(properties.getWebsocketPath()).isEqualTo("/medley/ws");
    }

    @Test
    void firstRequestRendersComponentAtItsRoute() throws Exception {
        mvc.perform(get("/counter"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("data-medley-id=\"root\"")))
                .andExpect(content().string(containsString("data-medley-id=\"root.3\"")))
                .andExpect(content().string(containsString("/medley/medley.js")));
    }

    @Test
    void frameworkAssetIsNotShadowedByRouting() throws Exception {
        mvc.perform(get("/medley/medley.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("window.medley")));
    }

    @Test
    void unmappedPathReturns404() throws Exception {
        mvc.perform(get("/no-such-route")).andExpect(status().isNotFound());
    }
}
