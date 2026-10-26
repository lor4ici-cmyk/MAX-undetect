package app.maxund.privacy;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.*;

/** Hooks are specific to the pinned MAX 26.34.1 protocol and UI. No network client. */
public final class Undetect {
    private static Context context;
    static WeakReference<Activity> foreground = new WeakReference<>(null);
    private static final ThreadLocal<Boolean> bypass = new ThreadLocal<>();
    private static final LinkedHashMap<String, Pending> pending = new LinkedHashMap<>();
    static boolean unlocked;
    static long stoppedAt;
    public static void install(Context c) { context = c.getApplicationContext(); }
    static SharedPreferences prefs() { return context.getSharedPreferences("maxund_undetect", 0); }
    public static boolean enabled(String key) { return context != null && prefs().getBoolean(key, false); }
    static void set(String key, boolean value) { prefs().edit().putBoolean(key, value).apply(); }
    private static boolean hidden(String key) { return enabled("incognito") || enabled(key); }
    static Set<String> exceptions() { return new HashSet<>(prefs().getStringSet("exceptions", Collections.emptySet())); }
    static void exception(String id, boolean value) {
        Set<String> ids = exceptions(); if(value) ids.add(id); else ids.remove(id);
        prefs().edit().putStringSet("exceptions", ids).apply();
    }
    static final class Pending {
        final WeakReference<Object> sender; final Object request;
        Pending(Object s,Object r) { sender=new WeakReference<>(s); request=r; }
    }
    // Policy is separately testable without the obfuscated protocol classes.
    static boolean shouldBlock(int opcode, Map<?,?> data) {
        String id=String.valueOf(data.get("chatId"));
        if (exceptions().contains(id)) return false;
        if(opcode==50) return hidden("reads") && "READ_MESSAGE".equals(data.get("type"));
        if(opcode==65) {
            String kind=String.valueOf(data.get("type")).toUpperCase(Locale.ROOT);
            return kind.contains("AUDIO") || kind.contains("VOICE") || kind.contains("VIDEO") || kind.contains("RECORD")
                ? hidden("recording") : hidden("typing");
        }
        return false;
    }
    /** Return true only after completing the blocked request via its normal error callback. */
    public static boolean beforeRequest(Object sender,Object request,Object callback) {
        if(context==null || Boolean.TRUE.equals(bypass.get())) return false;
        try {
            Class<?> base=Class.forName("oyi");
            int opcode=((Number)base.getMethod("k").invoke(request)).intValue();
            Map<?,?> data=(Map<?,?>)base.getField("a").get(request);
            if(!shouldBlock(opcode,data)) return false;
            String id=String.valueOf(data.get("chatId"));
            if(callback != null) {
                Class<?> error=Class.forName("fyi");
                Object e=error.getConstructor(String.class,String.class,String.class)
                    .newInstance("undetect.suppressed","Blocked locally by Undetect","Действие скрыто Undetect");
                Class.forName("yxi").getMethod("g",error).invoke(callback,e);
            }
            if(opcode==50) synchronized(pending) {
                pending.put(id,new Pending(sender,request));
                while(pending.size()>100) pending.remove(pending.keySet().iterator().next());
            }
            return true;
        } catch(ReflectiveOperationException | RuntimeException e) {
            // Fail closed for private signals when their hook cannot complete. Never block login/messages.
            throw new IllegalStateException("Undetect protocol hook mismatch",e);
        }
    }
    public static boolean interactive(String key, boolean original) {
        return original && !("interactive".equals(key) && hidden("online"));
    }
    static String[] pendingIds() { synchronized(pending) { return pending.keySet().toArray(new String[0]); } }
    static boolean sendRead(String id) {
        Pending p; synchronized(pending) { p=pending.get(id); }
        if(p==null || p.sender.get()==null) return false;
        try {
            Class<?> cb=Class.forName("yxi");
            Object sink=Proxy.newProxyInstance(cb.getClassLoader(),new Class[]{cb},(proxy,m,a)-> {
                if(m.getName().equals("p")) return 0L;
                if(m.getName().equals("hashCode")) return System.identityHashCode(proxy);
                if(m.getName().equals("equals")) return proxy==a[0];
                if(m.getName().equals("toString")) return "UndetectManualRead";
                return null;
            });
            bypass.set(true);
            Class.forName("q7c").getMethod("g",Class.forName("oyi"),boolean.class,long.class,cb)
                .invoke(p.sender.get(),p.request,true,30000L,sink);
            synchronized(pending) { pending.remove(id); }
            return true; // Queued, not a claim of server acceptance.
        } catch(ReflectiveOperationException | RuntimeException e) { return false; }
        finally { bypass.remove(); }
    }
    public static View settings(View original) {
        LinearLayout layout=new LinearLayout(original.getContext()); layout.setOrientation(1);
        Button button=new Button(original.getContext());button.setText("Undetect · Конфиденциальность");
        button.setOnClickListener(v->v.getContext().startActivity(new Intent(v.getContext(),UndetectActivity.class)));
        layout.addView(button,new LinearLayout.LayoutParams(-1,-2));
        layout.addView(original,new LinearLayout.LayoutParams(-1,0,1));return layout;
    }
    public static Notification notification(Notification n) {
        if(!enabled("notifications")) return n;
        // Strip message history, people, actions and custom views as well as visible text.
        Bundle b=new Bundle(); b.putCharSequence(Notification.EXTRA_TITLE,"MAXund");
        b.putCharSequence(Notification.EXTRA_TEXT,"Новое уведомление");n.extras=b;
        n.actions=null;n.contentView=null;n.bigContentView=null;n.headsUpContentView=null;
        n.tickerText="Новое уведомление";n.publicVersion=null;n.visibility=Notification.VISIBILITY_SECRET;
        return Notification.Builder.recoverBuilder(context,n).setStyle(null)
            .setLargeIcon((android.graphics.drawable.Icon)null).setSubText(null).setContentInfo(null)
            .setCustomContentView(null).setCustomBigContentView(null).setCustomHeadsUpContentView(null)
            .setExtras(b).build();
    }
    public static boolean blockAutoload(Object place) {
        return enabled("autoload") && place instanceof Enum && ((Enum<?>)place).name().equals("AUTOLOAD");
    }
    public static boolean confirmCall(Object router,Object route,Object transition) {
        if(!enabled("calls") || Boolean.TRUE.equals(bypass.get())) return false;
        try {
            Object factory=route.getClass().getField("g").get(route);
            if(factory==null || !(factory.getClass().getName().equals("cl1") || factory.getClass().getName().equals("dl1"))) return false;
            Activity a=foreground.get();if(a==null || a.isFinishing()) return true;
            new Handler(Looper.getMainLooper()).post(()->new AlertDialog.Builder(a)
                .setTitle("Начать звонок?").setMessage("Микрофон или камера будут доступны после разрешения Android.")
                .setNegativeButton("Отмена",null).setPositiveButton("Позвонить",(d,w)-> {
                    try {
                        bypass.set(true);
                        Class.forName("ytc").getMethod("h",Class.forName("dk5"),Class.forName("v7"))
                            .invoke(router,route,transition);
                    } catch(ReflectiveOperationException e) { Toast.makeText(a,"Не удалось начать звонок",1).show(); }
                    finally { bypass.remove(); }
                }).show());return true;
        } catch(ReflectiveOperationException e) { throw new IllegalStateException("Undetect call hook mismatch",e); }
    }
    static void resume(Activity a) {
        foreground=new WeakReference<>(a);
        if(enabled("lock") && !unlocked && !(a instanceof UndetectLockActivity)) {
            a.startActivity(new Intent(a,UndetectLockActivity.class));
        }
    }
    static void background() { unlocked=false; foreground.clear(); }
}
