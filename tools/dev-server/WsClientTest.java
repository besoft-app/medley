import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Connects to the DevServer WebSocket, sends the real event protocol, and prints each
 * patch batch the server returns. This exercises the exact path medley.js uses.
 */
public class WsClientTest {

    public static void main(String[] args) throws Exception {
        String[] script = {"increment", "increment", "decrement", "reset"};
        AtomicInteger replyIndex = new AtomicInteger(0);
        CountDownLatch done = new CountDownLatch(script.length);

        HttpClient client = HttpClient.newHttpClient();
        WebSocket ws = client.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:8082/"), new WebSocket.Listener() {
                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        int i = replyIndex.getAndIncrement();
                        String action = i < script.length ? script[i] : "?";
                        System.out.println("  ← after '" + action + "': " + data);
                        done.countDown();
                        webSocket.request(1);
                        return null;
                    }
                })
                .get(5, TimeUnit.SECONDS);

        System.out.println("Connected. Sending event sequence: " + String.join(", ", script));
        for (String action : script) {
            String msg = "{\"componentId\":\"root\",\"action\":\"" + action + "\"}";
            System.out.println("  → " + msg);
            ws.sendText(msg, true);
            Thread.sleep(150); // keep replies ordered for readable output
        }

        boolean ok = done.await(5, TimeUnit.SECONDS);
        System.out.println(ok ? "\nAll replies received." : "\nTIMEOUT waiting for replies.");
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "done");
        System.exit(ok ? 0 : 1);
    }
}
