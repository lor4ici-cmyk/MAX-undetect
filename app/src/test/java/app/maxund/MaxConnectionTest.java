package app.maxund;

import okhttp3.*;
import okio.ByteString;
import org.json.JSONObject;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class MaxConnectionTest {
    private static class Socket implements WebSocket {
        String sent;
        public Request request() { return new Request.Builder().url("https://example.test").build(); }
        public long queueSize() { return 0; }
        public boolean send(String text) { sent = text; return true; }
        public boolean send(ByteString bytes) { throw new AssertionError("No binary uploads expected"); }
        public boolean close(int code, String reason) { return true; }
        public void cancel() {}
    }
    @Test public void errorUsesOriginalRequestStageAndDoesNotLeakPayload() throws Exception {
        AtomicReference<String> error = new AtomicReference<>(); AtomicBoolean accepted = new AtomicBoolean();
        MaxConnection c = new MaxConnection(new MaxConnection.Events() {
            public void ready() { fail(); }
            public void packet(JSONObject p) { fail(); }
            public void error(String message) { error.set(message); }
        });
        try {
            Socket socket = new Socket();
            var field = MaxConnection.class.getDeclaredField("socket"); field.setAccessible(true); field.set(c, socket);
            c.call(17, Protocol.object("phone", "+79990000000"), p -> accepted.set(true));
            int seq = new JSONObject(socket.sent).getInt("seq");
            c.onMessage(socket, Protocol.object("seq", seq, "cmd", 3, "payload", Protocol.object("error", "auth.phone.limit", "token", "private-token")).toString());
            assertFalse(accepted.get()); assertTrue(error.get().contains("op=17"));
            assertFalse(error.get().contains("private-token")); assertFalse(error.get().contains("79990000000"));
        } finally { c.close(); }
    }
}
