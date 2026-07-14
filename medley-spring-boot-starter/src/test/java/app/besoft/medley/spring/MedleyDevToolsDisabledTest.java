package app.besoft.medley.spring;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

import app.besoft.medley.spring.fixtures.TestApplication;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Stage 5, increment 2 — the dev-tools gate, at its <b>default</b> (disabled). This is the security
 * property of the increment: an application that simply has the starter on its classpath must not leak
 * {@code @State} through the inspector. With the flag unset, the snapshot endpoint does not exist and
 * the SSR shell contains no reference to the overlay, so it cannot be switched on from the browser.
 */
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class MedleyDevToolsDisabledTest {

    @Autowired
    MockMvc mvc;

    @Test
    void treeEndpointDoesNotExist() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/cards").session(session)).andExpect(status().isOk());

        mvc.perform(get("/medley/devtools/tree").session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    void shellDoesNotLoadTheOverlay() throws Exception {
        mvc.perform(get("/counter"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/medley/medley.js")))
                .andExpect(content().string(not(containsString("/medley/devtools.js"))));
    }
}
