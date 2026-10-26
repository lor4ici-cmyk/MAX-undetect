package app.maxund;

import app.maxund.privacy.PrivacyLifecycle;
import android.Manifest;
import android.app.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Looper;
import android.view.WindowManager;
import java.time.Duration;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33)
public class PrivacyLifecycleTest {
    private static class App extends Application {
        List<String> revoked = new ArrayList<>();
        int servicesStopped;
        boolean granted;
        @Override public int checkSelfPermission(String p) { return granted ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED; }
        @Override public String getPackageName() { return "ru.oneme.app"; }
        @Override public boolean stopService(Intent i) { servicesStopped++; return true; }
        @Override public void revokeSelfPermissionsOnKill(Collection<String> p) { revoked.addAll(p); }
    }
    private PrivacyLifecycle guard(App app) throws Exception {
        var constructor = PrivacyLifecycle.class.getDeclaredConstructor(Application.class);
        constructor.setAccessible(true); return constructor.newInstance(app);
    }
    @Test public void backgroundSchedulesRevocationOfGrantedMediaOnly() throws Exception {
        App app = new App(); app.granted = true; PrivacyLifecycle g = guard(app);
        g.onActivityStarted(null); g.onActivityStopped(null);
        assertTrue(app.revoked.isEmpty());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1300));
        assertEquals(new HashSet<>(Arrays.asList(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)), new HashSet<>(app.revoked));
        assertEquals(2, app.servicesStopped);
        assertFalse(org.robolectric.shadows.ShadowProcess.wasKilled(android.os.Process.myPid()));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(5100));
        assertTrue(org.robolectric.shadows.ShadowProcess.wasKilled(android.os.Process.myPid()));
    }
    @Test public void switchingAppScreensDoesNotRevokePermissions() throws Exception {
        App app = new App(); app.granted = true; PrivacyLifecycle g = guard(app);
        g.onActivityStarted(null); g.onActivityStopped(null); g.onActivityStarted(null);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertTrue(app.revoked.isEmpty()); assertEquals(0, app.servicesStopped);
        assertFalse(org.robolectric.shadows.ShadowProcess.wasKilled(android.os.Process.myPid()));
    }
    @Test public void backgroundWithoutMediaGrantsDoesNotRequestProcessKill() throws Exception {
        App app = new App(); PrivacyLifecycle g = guard(app);
        g.onActivityStarted(null); g.onActivityStopped(null);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertTrue(app.revoked.isEmpty());
        assertFalse(org.robolectric.shadows.ShadowProcess.wasKilled(android.os.Process.myPid()));
    }
    @Test public void taskSnapshotsAreProtected() throws Exception {
        App app = new App(); PrivacyLifecycle g = guard(app);
        var controller = Robolectric.buildActivity(Activity.class).setup();
        try {
            g.onActivityCreated(controller.get(), null);
            assertTrue((controller.get().getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0);
        } finally { controller.pause().stop().destroy(); }
    }
}
