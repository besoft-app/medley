package app.besoft.medley.spring;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.besoft.medley.spring.fixtures.DevToolsProbeComponent;
import app.besoft.medley.spring.fixtures.TestApplication;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Stage 5, increment 2 — the dev-tools inspector, <b>enabled</b>. Two halves are proven here:
 *
 * <ul>
 *   <li><b>the tree snapshot</b> — {@code GET /medley/devtools/tree} reports the caller's own live
 *       component tree: the routed root <em>and</em> its nested child, each with its current
 *       {@code @State} / {@code @Param} values and its parent link. The {@code /cards} fixture nests a
 *       stateful {@code counter-card} whose {@code start} param seeds {@code count}, so the snapshot
 *       shows real post-{@code onInit} state, not template defaults;</li>
 *   <li><b>session isolation</b> — the endpoint takes no session identifier: it reads the requesting
 *       HTTP session only, so a caller that has not loaded a Medley page sees an empty tree and can
 *       never address someone else's components.</li>
 * </ul>
 *
 * <p>The SSR shell must also load {@code devtools.js} <em>before</em> {@code medley.js}: the overlay
 * taps the patch stream by wrapping {@code WebSocket} before the runtime opens one (which is what
 * keeps the production runtime free of dev-tools hooks), so the order is load-bearing.</p>
 */
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "medley.devtools.enabled=true")
@AutoConfigureMockMvc
class MedleyDevToolsIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TemplateRegistry templates;

    @Test
    void shellLoadsTheOverlayBeforeTheRuntime() throws Exception {
        String html = mvc.perform(get("/cards"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/medley/devtools.js")))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(html.indexOf("/medley/devtools.js"))
                .as("devtools.js must be loaded before medley.js so it can wrap WebSocket in time")
                .isLessThan(html.indexOf("/medley/medley.js"));
    }

    @Test
    void treeReportsTheRootAndItsNestedChildWithLiveState() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/cards").session(session)).andExpect(status().isOk());

        mvc.perform(get("/medley/devtools/tree").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                // the routed root: its own @State, and no parent
                .andExpect(jsonPath("$.components[0].id").value("root"))
                .andExpect(jsonPath("$.components[0].name").value("card-host"))
                .andExpect(jsonPath("$.components[0].type").value("CardHostComponent"))
                .andExpect(jsonPath("$.components[0].parent").value(nullValue()))
                .andExpect(jsonPath("$.components[0].state.seed").value(10))
                .andExpect(jsonPath("$.components[0].state.hostClicks").value(0))
                // the nested child: bound @Param, @State seeded from it in onInit, and its owner
                .andExpect(jsonPath("$.components[1].id").value("root.1::counter-card"))
                .andExpect(jsonPath("$.components[1].name").value("counter-card"))
                .andExpect(jsonPath("$.components[1].parent").value("root"))
                .andExpect(jsonPath("$.components[1].params.start").value(10))
                .andExpect(jsonPath("$.components[1].state.count").value(10));
    }

    @Test
    void treeOfACallerWithNoMedleySessionIsEmpty() throws Exception {
        // No page load in this session: nothing to inspect. The endpoint accepts no session id, so
        // this is also the only tree such a caller can ever obtain.
        mvc.perform(get("/medley/devtools/tree").session(new MockHttpSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.components").isEmpty());
    }

    @Test
    void overlayScriptIsServedAsAStaticAsset() throws Exception {
        mvc.perform(get("/medley/devtools.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("medley devtools")));
    }

    /**
     * {@code @State} holds whatever the application put there, so a field the snapshot cannot render must
     * degrade to a label — never take the request down with it. The nastiest case is a self-referencing
     * collection: Jackson recurses into a {@link StackOverflowError}, which is an {@link Error} and would
     * sail straight through a {@code catch (RuntimeException)} into a 500.
     */
    @Test
    void awkwardStateDegradesToALabelInsteadOfFailingTheRequest() throws Exception {
        MedleySession session = new MedleySession(templates);
        session.mount("root", new DevToolsProbeComponent());
        MockHttpSession httpSession = new MockHttpSession();
        httpSession.setAttribute(MedleySession.class.getName(), session);

        mvc.perform(get("/medley/devtools/tree").session(httpSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components[0].state.selfReferencing")
                        .value("<ArrayList: self-referencing>"))
                .andExpect(jsonPath("$.components[0].state.exploding")
                        .value("<Exploding: not serializable>"))
                .andExpect(jsonPath("$.components[0].state.huge")
                        .value("<ArrayList: too large to inline>"))
                // …while an ordinary field is still reported as a real value
                .andExpect(jsonPath("$.components[0].state.ok").value(42));
    }
}
