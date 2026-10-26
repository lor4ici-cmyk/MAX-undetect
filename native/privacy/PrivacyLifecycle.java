package app.maxund.privacy;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import java.util.ArrayList;

/** No networking or persistence. Revoke media grants when all app activities stop. */
public final class PrivacyLifecycle implements Application.ActivityLifecycleCallbacks, Runnable {
    private final Application application;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int started;
    private final Runnable finishRevocation = new Runnable() {
        public void run() {
            if (started == 0) android.os.Process.killProcess(android.os.Process.myPid());
        }
    };
    private PrivacyLifecycle(Application app) { application = app; }
    public static void install(Application app) { app.registerActivityLifecycleCallbacks(new PrivacyLifecycle(app)); }
    public void onActivityCreated(Activity a, Bundle b) { a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE); }
    public void onActivityStarted(Activity a) {
        started++; handler.removeCallbacks(this); handler.removeCallbacks(finishRevocation);
    }
    public void onActivityStopped(Activity a) {
        started = Math.max(0, started - 1);
        if (started == 0) handler.postDelayed(this, 1200);
    }
    public void run() {
        if (started != 0) return;
        // Background camera/microphone calls are deliberately ended by this policy.
        for (String name : new String[]{"one.me.calls.impl.service.VoIpCallService", "one.me.calls.impl.service.CallServiceImpl"}) {
            try { application.stopService(new Intent().setClassName(application.getPackageName(), name)); }
            catch (SecurityException ignored) { }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            ArrayList<String> grants = new ArrayList<>();
            for (String permission : new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}) {
                if (application.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) grants.add(permission);
            }
            if (!grants.isEmpty()) {
                application.revokeSelfPermissionsOnKill(grants);
                // The platform call posts asynchronously to PermissionController.
                // Do not kill the caller before that request has been delivered.
                handler.postDelayed(finishRevocation, 5000);
            }
        }
    }
    public void onActivityResumed(Activity a) { a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE); }
    public void onActivityPaused(Activity a) { }
    public void onActivitySaveInstanceState(Activity a, Bundle b) { }
    public void onActivityDestroyed(Activity a) { }
}
