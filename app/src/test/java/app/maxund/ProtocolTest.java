package app.maxund;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProtocolTest {
    @Test public void currentWebCompatibilityProfileUsesCanonicalEndpoint() throws Exception {
        assertEquals("wss://api.oneme.ru/websocket", Protocol.ENDPOINT);
        assertEquals("26.8.4", Protocol.hello("device").getJSONObject("userAgent").getString("appVersion"));
    }
    @Test public void structuredAndUppercaseErrorsRemainUsefulWithoutRawDescriptions() throws Exception {
        String upper = Protocol.rejection(17, 3, Protocol.object("error", "AUTH_REQUEST_FAILED"));
        assertTrue(upper.contains("error=auth_request_failed"));
        String structured = Protocol.rejection(17, 3, Protocol.object("error", Protocol.object("code", "PHONE-NOT-ALLOWED", "message", "captcha required for +79990000000 with token secret-token")));
        assertTrue(structured.contains("error=phone-not-allowed"));
        assertTrue(structured.contains("category=captcha_required"));
        assertFalse(structured.contains("79990000000")); assertFalse(structured.contains("secret-token"));
        assertTrue(structured.contains("format=object"));
        String plain = Protocol.rejection(17, 3, "Too many requests for +79990000000 secret-token");
        assertTrue(plain.contains("category=rate_limit")); assertFalse(plain.contains("79990000000"));
        assertTrue(Protocol.rejection(17, 3, Protocol.object("error", "my-secret-token")).contains("error=скрыт"));
        assertTrue(Protocol.rejection(17, 3, (Object) null).contains("format=empty-payload"));
    }
    @Test public void passwordChallengeAndPasswordRequestAreSupported() throws Exception {
        assertEquals("track", Protocol.passwordTrack(Protocol.object("passwordChallenge", Protocol.object("trackId", "track"))));
        assertEquals("track", Protocol.passwordTrack(Protocol.object("passwordChallenge", new JSONObject(), "trackId", "track")));
        assertEquals("", Protocol.passwordTrack(Protocol.object("trackId", "not-a-challenge")));
        JSONObject frame = Protocol.frame(1, 115, Protocol.password("track", "a test password"));
        assertEquals(2, frame.getJSONObject("payload").length());
        assertEquals("a test password", frame.getJSONObject("payload").getString("password"));
    }
    @Test public void verifyTokenCannotAccidentallyBecomeLoginToken() throws Exception {
        JSONObject p = Protocol.object("token", "verification-only");
        assertEquals("", Protocol.loginToken(p, false));
        assertEquals("verification-only", Protocol.loginToken(p, true));
        assertEquals("", Protocol.loginToken(Protocol.object("passwordChallenge", new JSONObject(), "token", "challenge"), true));
        assertEquals("session", Protocol.loginToken(Protocol.object("tokenAttrs", Protocol.object("LOGIN", Protocol.object("token", "session"))), false));
    }
    @Test public void diagnosticsRevealStageButNotServerSecrets() throws Exception {
        String safe = Protocol.rejection(17, 3, Protocol.object("error", "auth.phone.limit", "token", "secret-token", "phone", "+79990000000"));
        assertTrue(safe.contains("op=17")); assertTrue(safe.contains("auth.phone.limit"));
        assertFalse(safe.contains("secret-token")); assertFalse(safe.contains("79990000000"));
        String unsafe = Protocol.rejection(18, 3, Protocol.object("error", "password=secret and phone=7999"));
        assertFalse(unsafe.contains("password=secret")); assertTrue(unsafe.contains("скрыт"));
    }
    @Test public void analyticsAndContactUploadCannotBeSent() throws Exception {
        for (int opcode : new int[]{5, 32, 34, 46, 65, 80, 81, 82, 87, 202}) {
            try { Protocol.frame(1, opcode, new JSONObject()); fail("Allowed opcode " + opcode); }
            catch (IllegalArgumentException expected) {}
        }
    }
    @Test public void messageContainsOnlyExplicitTextAndEmptyAttachments() throws Exception {
        JSONObject frame = Protocol.frame(7, 64, Protocol.message(-123, "Привет <script> & 👋"));
        assertEquals(11, frame.getInt("ver")); assertEquals(64, frame.getInt("opcode"));
        JSONObject p = frame.getJSONObject("payload"); assertEquals(-123, p.getLong("chatId"));
        JSONObject m = p.getJSONObject("message"); assertEquals("Привет <script> & 👋", m.getString("text"));
        assertEquals(0, m.getJSONArray("attaches").length()); assertEquals(0, m.getJSONArray("elements").length());
        assertFalse(p.has("contacts")); assertFalse(m.has("location"));
        assertTrue(m.getLong("cid") > 0); assertTrue(m.getLong("cid") < 9007199254740991L);
        assertNotEquals(m.getLong("cid"), Protocol.message(-123, "Следующее").getJSONObject("message").getLong("cid"));
    }
    @Test public void genericHandshakeNeverRequiresDeviceInformation() throws Exception {
        JSONObject h = Protocol.hello("random-session-id");
        assertEquals("random-session-id", h.getString("deviceId"));
        JSONObject ua = h.getJSONObject("userAgent");
        assertEquals("WEB", ua.getString("deviceType")); assertEquals("Linux", ua.getString("osVersion"));
        assertFalse(h.has("phone")); assertFalse(h.has("contacts"));
    }
    @Test public void loginUsesOnlyProvidedSession() throws Exception {
        JSONObject p = Protocol.login("test-token");
        assertEquals("test-token", p.getString("token")); assertEquals(-1, p.getInt("presenceSync"));
        assertEquals(0, p.getInt("contactsSync")); assertFalse(p.has("contactIds"));
    }
    @Test public void emptyAndOversizedMessagesAreRejected() throws Exception {
        for (String s : new String[]{" ", "x".repeat(4001)}) {
            try { Protocol.message(1, s); fail(); } catch (IllegalArgumentException expected) {}
        }
    }
}
