package app.maxund;

import okhttp3.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** No disk cache, analytics, packet logging, uploads or arbitrary endpoints. */
public final class MaxConnection extends WebSocketListener {
    public interface Events { void ready(); void packet(JSONObject packet); void error(String message); }
    private final Events events;
    private final OkHttpClient http = new OkHttpClient.Builder().followRedirects(false)
        .followSslRedirects(false).pingInterval(30, TimeUnit.SECONDS).build();
    private final ScheduledExecutorService clock = Executors.newSingleThreadScheduledExecutor();
    private record Pending(int opcode, Consumer<JSONObject> callback, ScheduledFuture<?> timer) {}
    private final Map<Integer, Pending> pending = new HashMap<>();
    private WebSocket socket;
    private int sequence;
    private volatile boolean closed;
    public MaxConnection(Events events) { this.events = events; }
    public synchronized void connect() {
        if (closed || socket != null) return;
        socket = http.newWebSocket(new Request.Builder().url(Protocol.ENDPOINT)
            .header("Origin", "https://web.max.ru").header("User-Agent", Protocol.UA).build(), this);
    }
    public synchronized void call(int opcode, JSONObject payload, Consumer<JSONObject> callback) {
        if (closed || socket == null) { events.error("Нет соединения. Вернитесь на экран входа."); return; }
        int seq = ++sequence;
        try {
            String frame = Protocol.frame(seq, opcode, payload).toString();
            ScheduledFuture<?> timer = clock.schedule(() -> {
                synchronized (this) { if (pending.remove(seq) == null) return; }
                events.error("Сервер не ответил. Повторите действие после проверки соединения.");
            }, 25, TimeUnit.SECONDS);
            pending.put(seq, new Pending(opcode, callback, timer));
            if (!socket.send(frame)) { pending.remove(seq); timer.cancel(false); events.error("Соединение закрыто."); }
        } catch (JSONException | IllegalArgumentException e) { events.error("Операция отклонена локально."); }
    }
    @Override public void onOpen(WebSocket ws, Response response) { events.ready(); }
    @Override public void onMessage(WebSocket ws, String text) {
        if (closed) return;
        try {
            JSONObject packet = new JSONObject(text);
            Pending p;
            synchronized (this) { p = pending.remove(packet.optInt("seq", -1)); }
            if (p != null) {
                p.timer.cancel(false);
                JSONObject payload = packet.optJSONObject("payload");
                if (packet.optInt("cmd") == 3 || (payload != null && payload.has("error"))) {
                    events.error(Protocol.rejection(p.opcode, packet.optInt("cmd"), packet.opt("payload")));
                } else p.callback.accept(payload == null ? new JSONObject() : payload);
            } else events.packet(packet);
        } catch (Exception e) { events.error("Неподдерживаемый ответ MAX."); }
    }
    @Override public void onFailure(WebSocket ws, Throwable t, Response response) {
        if (!closed) events.error("Не удалось подключиться к MAX. Проверьте сеть; сервер может не поддерживать этот клиент.");
        close();
    }
    @Override public void onClosed(WebSocket ws, int code, String reason) {
        if (!closed) events.error("MAX закрыл соединение. Войдите заново.");
        close();
    }
    public synchronized void close() {
        if (closed) return;
        closed = true;
        pending.values().forEach(p -> p.timer.cancel(false)); pending.clear();
        if (socket != null) socket.cancel(); socket = null;
        clock.shutdownNow(); http.dispatcher().executorService().shutdown(); http.connectionPool().evictAll();
    }
}
