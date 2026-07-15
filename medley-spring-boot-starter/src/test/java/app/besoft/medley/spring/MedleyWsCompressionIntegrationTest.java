package app.besoft.medley.spring;

import static org.assertj.core.api.Assertions.assertThat;

import app.besoft.medley.spring.fixtures.TestApplication;

import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Extension;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;
import jakarta.websocket.ContainerProvider;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/**
 * Stage 5, increment 3 — the Medley WebSocket wire is compressed by <b>permessage-deflate</b>
 * (RFC 7692), at the transport layer. This was chosen over a binary/CBOR patch format: measurement
 * showed deflate shrinks the payloads that actually matter — repetitive HTML in {@code Replace}/
 * {@code Insert} and batches of patches with repeated keys — by 3–4×, while CBOR would touch only
 * structural overhead and nothing of the HTML content (MEDLEY_DESIGN §5.3).
 *
 * <p>Crucially, this needs <b>no code</b>: the embedded Tomcat offers {@code permessage-deflate} in
 * {@code Constants.INSTALLED_EXTENSIONS}, and its server handshake negotiates it whenever the client
 * asks — which every browser does. This test is the guard: it opens a real WebSocket that offers the
 * extension and asserts the server negotiated it, so a future dependency bump or handshake change that
 * silently drops compression fails here instead of quietly regressing every deployment's bandwidth.</p>
 */
@SpringBootTest(classes = TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MedleyWsCompressionIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    /** A bare {@code permessage-deflate} offer — the client half of the negotiation. */
    private static final Extension PERMESSAGE_DEFLATE = new Extension() {
        @Override
        public String getName() {
            return "permessage-deflate";
        }

        @Override
        public List<Parameter> getParameters() {
            return List.of();
        }
    };

    @Test
    void serverNegotiatesPermessageDeflateSoTheWireIsCompressed() throws Exception {
        // SSR first so the handshake carries a real session cookie, mirroring a browser exactly.
        ResponseEntity<String> ssr = rest.getForEntity("/counter", String.class);
        String sessionCookie = ssr.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];

        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        ClientEndpointConfig config = ClientEndpointConfig.Builder.create()
                .extensions(List.of(PERMESSAGE_DEFLATE))
                .configurator(new ClientEndpointConfig.Configurator() {
                    @Override
                    public void beforeRequest(java.util.Map<String, List<String>> headers) {
                        headers.put(HttpHeaders.COOKIE, List.of(sessionCookie));
                    }
                })
                .build();

        CountDownLatch open = new CountDownLatch(1);
        AtomicReference<List<Extension>> negotiated = new AtomicReference<>();
        Endpoint endpoint = new Endpoint() {
            @Override
            public void onOpen(Session session, EndpointConfig cfg) {
                negotiated.set(session.getNegotiatedExtensions());
                open.countDown();
            }
        };

        Session session = container.connectToServer(endpoint, config,
                URI.create("ws://localhost:" + port + "/medley/ws"));
        try {
            assertThat(open.await(5, TimeUnit.SECONDS)).as("WebSocket must open").isTrue();
            assertThat(negotiated.get())
                    .as("Tomcat must negotiate permessage-deflate — the whole reason 5.3 needed no "
                            + "binary format. If this fails, the patch stream is no longer compressed on "
                            + "the wire and a dependency/handshake change silently regressed it.")
                    .anyMatch(e -> "permessage-deflate".equals(e.getName()));
        } finally {
            session.close();
        }
    }
}
