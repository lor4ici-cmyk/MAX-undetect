package app.maxund;

import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MainActivityTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    @Before public void open() { controller = Robolectric.buildActivity(MainActivity.class).setup(); activity = controller.get(); }
    @After public void close() { controller.pause().stop().destroy(); }
    private TextView find(View view, String label) {
        if (view instanceof TextView && ((TextView) view).getText().toString().equals(label)) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) { TextView r = find(group.getChildAt(i), label); if (r != null) return r; }
        }
        return null;
    }
    private TextView find(String label) { return find(activity.getWindow().getDecorView(), label); }
    @Test public void opensLoginWithScreenshotProtectionAndNoConnection() throws Exception {
        assertNotNull(find("MAXund")); assertNotNull(find("Получить код"));
        assertTrue((activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0);
        var connection = MainActivity.class.getDeclaredField("connection"); connection.setAccessible(true);
        assertNull("Must not contact MAX before explicit login", connection.get(activity));
    }
    @Test public void invalidPhoneDoesNotStartNetwork() throws Exception {
        find("Получить код").performClick();
        assertNotNull(find("Введите номер в международном формате: +79991234567"));
        var connection = MainActivity.class.getDeclaredField("connection"); connection.setAccessible(true);
        assertNull(connection.get(activity));
    }
    @Test public void serverChallengeShowsPasswordScreenWithoutStoringPassword() throws Exception {
        var method = MainActivity.class.getDeclaredMethod("handleAuth", org.json.JSONObject.class, boolean.class);
        method.setAccessible(true);
        method.invoke(activity, Protocol.object("passwordChallenge", Protocol.object("trackId", "test-track")), false);
        assertNotNull(find("Введите пароль MAX")); assertNotNull(find("Подтвердить пароль"));
        var track = MainActivity.class.getDeclaredField("passwordTrack"); track.setAccessible(true);
        assertEquals("test-track", track.get(activity));
    }
    @Test public void backgroundClosesTransportAndReturnsToLogin() throws Exception {
        MaxConnection transport = new MaxConnection(new MaxConnection.Events() {
            public void ready() { fail("No network connection expected"); }
            public void packet(org.json.JSONObject p) { fail("No packets expected"); }
            public void error(String message) {}
        });
        var connection = MainActivity.class.getDeclaredField("connection"); connection.setAccessible(true);
        connection.set(activity, transport);
        controller.pause().stop();
        var closed = MaxConnection.class.getDeclaredField("closed"); closed.setAccessible(true);
        assertTrue(closed.getBoolean(transport));
        for (String field : new String[]{"connection", "loginToken", "smsToken"}) {
            var f = MainActivity.class.getDeclaredField(field); f.setAccessible(true); assertNull(f.get(activity));
        }
        controller.start().resume();
        assertNotNull(find("Получить код"));
    }
}
