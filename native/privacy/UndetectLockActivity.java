package app.maxund.privacy;
import android.app.*;
import android.hardware.biometrics.BiometricPrompt;
import android.os.*;
import android.view.*;
import android.widget.*;

public final class UndetectLockActivity extends Activity {
    private CancellationSignal signal;
    private boolean prompting;
    public void onCreate(Bundle state) {
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);layout.setGravity(Gravity.CENTER);layout.setBackgroundColor(0xff101820);
        Button unlock=new Button(this);unlock.setText("Разблокировать MAXund");unlock.setOnClickListener(v->authenticate());layout.addView(unlock);setContentView(layout);
        authenticate();
    }
    private void authenticate() {
        if(prompting)return;prompting=true;signal=new CancellationSignal();
        new BiometricPrompt.Builder(this).setTitle("Разблокировать MAXund")
            .setAllowedAuthenticators(255 | 32768).build().authenticate(signal,getMainExecutor(),new BiometricPrompt.AuthenticationCallback() {
                public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) { prompting=false;Undetect.unlocked=true;finish(); }
                public void onAuthenticationError(int code,CharSequence message) { prompting=false; }
            });
    }
    public void onBackPressed() { moveTaskToBack(true); }
    public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); }
    protected void onDestroy() { if(signal!=null)signal.cancel();super.onDestroy(); }
}
