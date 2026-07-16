import app.besoft.medley.core.component.*;
import app.besoft.medley.core.diff.Patch;
import app.besoft.medley.core.template.TemplateRenderer;

import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Dependency-free harness that proves the Medley Stage-1 loop end to end:
 *   - HTTP server: SSR the counter + serve medley.js
 *   - WebSocket server (hand-rolled RFC6455): receive events, run the real core engine,
 *     send back JSON patches.
 *
 * This is NOT part of the shipped framework (Spring does this job in the real starter).
 * It exists only to execute the full path in this sandbox where Spring jars aren't available.
 */
public class DevServer {

    // --- the counter component (same logic as the demo) ---
    @Annotations.MedleyComponent("counter")
    public static class Counter extends Component {
        @Annotations.State int count = 0;
        @Annotations.Param String label = "Clicks";
        @Annotations.Action void increment() { count++; }
        @Annotations.Action void decrement() { if (count > 0) count--; }
        @Annotations.Action void reset() { count = 0; }
        public int getCount() { return count; }
        public String getLabel() { return label; }
    }

    static final String TEMPLATE = """
        <div class="counter">
          <button @click="decrement" *if="count > 0">−</button>
          <span>{{ label }}: {{ count }}</span>
          <button @click="increment">+</button>
          <button @click="reset" *if="count > 0">reset</button>
        </div>
        """;

    // one component instance per WS connection for this demo
    static final ConcurrentHashMap<Socket, ComponentInstance> SESSIONS = new ConcurrentHashMap<>();

    static String medleyJs;

    public static void main(String[] args) throws Exception {
        medleyJs = readMedleyJs();

        int httpPort = 8081;
        int wsPort = 8082;

        startHttp(httpPort, wsPort);
        startWebSocket(wsPort);

        System.out.println("HTTP  : http://localhost:" + httpPort + "/counter");
        System.out.println("WS    : ws://localhost:" + wsPort + "/");
        System.out.println("Server running. (this harness is for verification only)");

        // keep alive
        Thread.currentThread().join();
    }

    // ---------- HTTP ----------
    static void startHttp(int port, int wsPort) throws IOException {
        HttpServer http = HttpServer.create(new InetSocketAddress(port), 0);
        http.setExecutor(Executors.newCachedThreadPool());

        http.createContext("/medley/medley.js", ex -> {
            byte[] body = medleyJs.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/javascript; charset=utf-8");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(body); }
        });

        http.createContext("/counter", ex -> {
            Counter c = new Counter();
            ComponentInstance inst = new ComponentInstance("root", c, TemplateRenderer.of(TEMPLATE));
            String bodyHtml = inst.renderInitialHtml();
            String page = shell(bodyHtml, wsPort);
            byte[] body = page.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(body); }
        });

        http.start();
    }

    static String shell(String bodyHtml, int wsPort) {
        // point the client at the standalone WS port
        return """
            <!doctype html>
            <html lang="pl"><head><meta charset="utf-8">
            <title>Medley dev</title>
            <style>
              body{font-family:system-ui,sans-serif;margin:3rem}
              .counter{display:flex;gap:.75rem;align-items:center;font-size:1.25rem}
              button{font-size:1rem;padding:.4rem .8rem;cursor:pointer}
              medley-text{display:contents}
              medley-placeholder{display:none}
            </style></head>
            <body>
              <div id="medley-root" data-ws="ws://localhost:%d/">%s</div>
              <script src="/medley/medley.js"></script>
            </body></html>
            """.formatted(wsPort, bodyHtml);
    }

    // ---------- WebSocket (minimal RFC6455) ----------
    static void startWebSocket(int port) {
        Thread t = new Thread(() -> {
            try (ServerSocket server = new ServerSocket(port)) {
                while (true) {
                    Socket sock = server.accept();
                    Executors.newSingleThreadExecutor().submit(() -> handleWs(sock));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        t.setDaemon(true);
        t.start();
    }

    static void handleWs(Socket sock) {
        try {
            InputStream in = sock.getInputStream();
            OutputStream out = sock.getOutputStream();

            // --- handshake ---
            String request = readHttpHeaders(in);
            String key = null;
            for (String line : request.split("\r\n")) {
                if (line.toLowerCase().startsWith("sec-websocket-key:")) {
                    key = line.substring(line.indexOf(':') + 1).trim();
                }
            }
            if (key == null) { sock.close(); return; }
            String accept = Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-1").digest(
                            (key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.UTF_8)));
            String resp = "HTTP/1.1 101 Switching Protocols\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n";
            out.write(resp.getBytes(StandardCharsets.UTF_8));
            out.flush();

            // fresh component for this connection
            Counter c = new Counter();
            ComponentInstance inst = new ComponentInstance("root", c, TemplateRenderer.of(TEMPLATE));
            inst.renderInitialHtml(); // establish baseline tree for diffing
            SESSIONS.put(sock, inst);

            // --- frame loop ---
            for (;;) {
                String msg = readFrame(in);
                if (msg == null) break;
                String reply = onMessage(inst, msg);
                if (reply != null) writeFrame(out, reply);
            }
        } catch (Exception e) {
            // connection closed / error
        } finally {
            SESSIONS.remove(sock);
            try { sock.close(); } catch (IOException ignored) {}
        }
    }

    static String onMessage(ComponentInstance inst, String json) {
        // ultra-light JSON field extraction (harness only)
        String action = extract(json, "action");
        if (action == null) return "{\"op\":\"error\",\"message\":\"no action\"}";
        try {
            List<Patch> patches = inst.invokeAction(action);
            return PatchJson.encode(patches);
        } catch (Exception e) {
            return "{\"op\":\"error\",\"message\":\"" + e.getMessage() + "\"}";
        }
    }

    static String extract(String json, String field) {
        String needle = "\"" + field + "\"";
        int i = json.indexOf(needle);
        if (i < 0) return null;
        int colon = json.indexOf(':', i + needle.length());
        int q1 = json.indexOf('"', colon + 1);
        int q2 = json.indexOf('"', q1 + 1);
        if (q1 < 0 || q2 < 0) return null;
        return json.substring(q1 + 1, q2);
    }

    // ---------- low-level WS framing ----------
    static String readHttpHeaders(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b; int state = 0;
        while ((b = in.read()) != -1) {
            buf.write(b);
            // detect \r\n\r\n
            if (b == '\r' && (state == 0 || state == 2)) state++;
            else if (b == '\n' && (state == 1 || state == 3)) state++;
            else state = 0;
            if (state == 4) break;
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    static String readFrame(InputStream in) throws IOException {
        int b1 = in.read();
        if (b1 == -1) return null;
        int opcode = b1 & 0x0F;
        if (opcode == 0x8) return null; // close
        int b2 = in.read();
        if (b2 == -1) return null;
        boolean masked = (b2 & 0x80) != 0;
        long len = b2 & 0x7F;
        if (len == 126) {
            len = ((long) in.read() << 8) | in.read();
        } else if (len == 127) {
            len = 0;
            for (int i = 0; i < 8; i++) len = (len << 8) | in.read();
        }
        byte[] mask = new byte[4];
        if (masked) { for (int i = 0; i < 4; i++) mask[i] = (byte) in.read(); }
        byte[] data = new byte[(int) len];
        int read = 0;
        while (read < len) {
            int r = in.read(data, read, (int) len - read);
            if (r == -1) break;
            read += r;
        }
        if (masked) { for (int i = 0; i < data.length; i++) data[i] ^= mask[i % 4]; }
        return new String(data, StandardCharsets.UTF_8);
    }

    static void writeFrame(OutputStream out, String text) throws IOException {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(0x81); // FIN + text
        if (payload.length < 126) {
            frame.write(payload.length);
        } else if (payload.length < 65536) {
            frame.write(126);
            frame.write((payload.length >> 8) & 0xFF);
            frame.write(payload.length & 0xFF);
        } else {
            frame.write(127);
            for (int i = 7; i >= 0; i--) frame.write((int) ((long) payload.length >> (8 * i)) & 0xFF);
        }
        frame.write(payload);
        out.write(frame.toByteArray());
        out.flush();
    }

    static final String MEDLEY_JS_PATH =
            "medley-spring-boot-starter/src/main/resources/static/medley/medley.js";

    static String readMedleyJs() throws IOException {
        // Walk up from the working dir so the harness runs from tools/dev-server or the repo root.
        for (File dir = new File("").getAbsoluteFile(); dir != null; dir = dir.getParentFile()) {
            File f = new File(dir, MEDLEY_JS_PATH);
            if (f.isFile()) {
                return new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            }
        }
        throw new FileNotFoundException("medley.js not found: run from inside the medley repo (looked for "
                + MEDLEY_JS_PATH + " up from " + new File("").getAbsolutePath() + ")");
    }
}
