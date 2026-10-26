package app.maxund;

import org.json.*;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Arrays;
import java.util.HashSet;

/** Minimal text-client protocol adapted from nsdkinx/vkmax (MIT). */
public final class Protocol {
    public static final String ENDPOINT = "wss://api.oneme.ru/websocket";
    // Fixed compatibility values, never obtained from the user's device.
    public static final String UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36";
    private static final Set<Integer> ALLOWED = new HashSet<>(Arrays.asList(1, 6, 17, 18, 19, 49, 64, 115, 288, 289, 291));
    private static final AtomicLong cid = new AtomicLong(System.currentTimeMillis());
    public static JSONObject object(Object... values) throws JSONException {
        JSONObject o = new JSONObject();
        for (int i = 0; i < values.length; i += 2) o.put((String) values[i], values[i + 1]);
        return o;
    }
    public static JSONObject frame(int seq, int opcode, JSONObject payload) throws JSONException {
        if (!ALLOWED.contains(opcode)) throw new IllegalArgumentException("Operation blocked by privacy policy");
        return object("ver", 11, "cmd", 0, "seq", seq, "opcode", opcode, "payload", payload);
    }
    public static JSONObject hello(String deviceId) throws JSONException {
        return object("deviceId", deviceId, "userAgent", object("deviceType", "WEB", "locale", "ru",
            "deviceLocale", "ru", "osVersion", "Linux", "deviceName", "Chrome", "headerUserAgent", UA,
            "appVersion", "26.8.4", "screen", "1080x1920 1.0x", "timezone", "Europe/Moscow"));
    }
    public static JSONObject login(String token) throws JSONException {
        return object("interactive", true, "token", token, "chatsCount", 40, "chatsSync", 0,
            "contactsSync", 0, "presenceSync", -1, "draftsSync", 0);
    }
    public static String loginToken(JSONObject payload, boolean afterPassword) {
        JSONObject attrs = payload.optJSONObject("tokenAttrs");
        JSONObject login = attrs == null ? null : attrs.optJSONObject("LOGIN");
        String token = login == null ? "" : login.optString("token", "");
        if (token.isEmpty() && afterPassword && !payload.has("passwordChallenge")) {
            Object direct = payload.opt("token");
            if (direct instanceof String) token = (String) direct;
        }
        return token;
    }
    public static String passwordTrack(JSONObject payload) {
        JSONObject challenge = payload.optJSONObject("passwordChallenge");
        if (challenge == null) return "";
        String id = challenge.optString("trackId", "");
        return id.isEmpty() ? payload.optString("trackId", "") : id;
    }
    public static JSONObject password(String track, String password) throws JSONException {
        if (track.isEmpty() || password.isEmpty()) throw new IllegalArgumentException("Missing challenge or password");
        return object("trackId", track, "password", password);
    }
    /** No arbitrary server descriptions, tokens, numbers or JSON are displayed. */
    public static String rejection(int opcode, int cmd, Object raw) {
        JSONObject payload = raw instanceof JSONObject ? (JSONObject) raw : null;
        Object error = payload == null ? null : payload.opt("error");
        Object machine = error;
        if (error instanceof JSONObject) {
            JSONObject detail = (JSONObject) error;
            machine = detail.has("code") ? detail.opt("code") : detail.opt("error");
        }
        if (machine == null && payload != null) machine = payload.opt("errorCode");
        String code = safeErrorCode(machine);
        String category = errorCategory(payload, error, raw);
        String format = raw instanceof JSONObject ? (error instanceof JSONObject ? "object" : error instanceof String ? "string" : "other")
            : raw instanceof String ? "text-payload" : "empty-payload";
        String step = switch (opcode) {
            case 6 -> "инициализация"; case 17 -> "запрос кода"; case 18 -> "проверка кода";
            case 115 -> "пароль 2FA"; case 19 -> "вход"; default -> "операция";
        };
        return "MAX отклонил запрос: " + step + ". op=" + opcode + ", cmd=" + cmd + ", error=" + code
            + ", category=" + category + ", format=" + format
            + ". Причина входа ещё не подтверждена. Не повторяйте запросы кода подряд; можно попробовать вход по QR.";
    }
    private static String safeErrorCode(Object value) {
        if (!(value instanceof String)) return "скрыт";
        String text = ((String) value).toLowerCase(java.util.Locale.ROOT);
        Set<String> plain = new HashSet<>(Arrays.asList("forbidden", "unauthorized", "timeout", "unsupported", "captcha", "invalid", "internal"));
        Set<String> words = new HashSet<>(Arrays.asList("auth", "phone", "sms", "invalid", "limit", "rate", "blocked", "forbidden", "not", "allowed", "required", "supported", "unsupported", "request", "failed", "code", "expired", "token", "session", "login", "version", "client", "old", "too", "many", "requests", "attempts", "access", "denied", "captcha", "proto", "payload", "opcode", "internal", "server", "error", "validation", "device", "verification", "password", "challenge", "missing", "timeout", "bad", "number", "unavailable", "update"));
        if (text.length() > 64) return "скрыт";
        if (plain.contains(text)) return text;
        if (text.matches("[a-z]+(?:[._:-][a-z]+)+")) {
            for (String part : text.split("[._:-]")) if (!words.contains(part)) return "скрыт";
            return text;
        }
        return "скрыт";
    }
    private static String errorCategory(JSONObject payload, Object error, Object raw) {
        StringBuilder parts = new StringBuilder();
        // Values are examined only to choose fixed labels; never returned or logged.
        if (raw instanceof String) parts.append((String) raw);
        if (error instanceof String) parts.append(' ').append((String) error);
        JSONObject detail = error instanceof JSONObject ? (JSONObject) error : payload;
        if (detail != null) for (String key : Arrays.asList("code", "message", "description", "localizedMessage", "title")) {
            Object value = detail.opt(key);
            if (value instanceof String) parts.append(' ').append((String) value);
        }
        String text = parts.toString().toLowerCase(java.util.Locale.ROOT);
        if (text.contains("captcha") || text.contains("капч")) return "captcha_required";
        if (text.contains("limit") || text.contains("too many") || text.contains("слишком част")) return "rate_limit";
        if (text.contains("attest") || text.contains("fingerprint")) return "device_verification";
        if (text.contains("version") || text.contains("верси")) return "client_version";
        if (text.contains("proto.payload") || text.contains("field requirement")) return "request_format";
        if (text.contains("forbidden") || text.contains("not.allowed") || text.contains("not-allowed")) return "not_allowed";
        return "unknown";
    }
    public static JSONObject message(long chatId, String text) throws JSONException {
        if (text.trim().isEmpty() || text.length() > 4000) throw new IllegalArgumentException("Message length");
        return object("chatId", chatId, "notify", true, "message", object("text", text,
            "cid", cid.updateAndGet(previous -> Math.max(previous + 1, System.currentTimeMillis())),
            "elements", new JSONArray(), "attaches", new JSONArray()));
    }
    private Protocol() {}
}
