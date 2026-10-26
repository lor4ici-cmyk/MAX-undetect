package app.maxund.privacy;
import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=33)
public class UndetectTest {
 @Before public void setup() { Undetect.install(RuntimeEnvironment.getApplication());Undetect.prefs().edit().clear().commit(); }
 private Map<String,Object> data(String type) { Map<String,Object> d=new HashMap<>();d.put("type",type);d.put("chatId",12L);return d; }
 @Test public void defaultsNeverSuppressProtocol() {
  assertFalse(Undetect.shouldBlock(50,data("READ_MESSAGE")));assertFalse(Undetect.shouldBlock(65,data("AUDIO")));
  assertTrue(Undetect.interactive("interactive",true));
 }
 @Test public void readPolicyDoesNotAlterUnreadReactionLoginOrMessages() {
  Undetect.set("reads",true);assertTrue(Undetect.shouldBlock(50,data("READ_MESSAGE")));
  for(String t:new String[]{"SET_AS_UNREAD","READ_REACTION"})assertFalse(Undetect.shouldBlock(50,data(t)));
  for(int op:new int[]{17,18,19,64})assertFalse(Undetect.shouldBlock(op,data("READ_MESSAGE")));
 }
 @Test public void voiceAndTextCanBeControlledIndependently() {
  Undetect.set("recording",true);assertTrue(Undetect.shouldBlock(65,data("RECORDING_AUDIO")));
  assertFalse(Undetect.shouldBlock(65,data("TEXT")));
  Undetect.set("recording",false);Undetect.set("typing",true);
  assertFalse(Undetect.shouldBlock(65,data("VIDEO")));assertTrue(Undetect.shouldBlock(65,data("TEXT")));
 }
 @Test public void incognitoAndExceptionsHaveExplicitScope() {
  Undetect.set("incognito",true);assertTrue(Undetect.shouldBlock(50,data("READ_MESSAGE")));
  assertTrue(Undetect.shouldBlock(65,data("TEXT")));assertTrue(Undetect.shouldBlock(65,data("AUDIO")));
  Undetect.exception("12",true);assertFalse(Undetect.shouldBlock(50,data("READ_MESSAGE")));
  assertFalse(Undetect.shouldBlock(65,data("TEXT")));
  assertFalse(Undetect.interactive("interactive",true));assertTrue(Undetect.interactive("getMessages",true));
 }
 @Test public void masksOnlyInteractiveFieldAndNeverTurnsFalseIntoTrue() {
  Undetect.set("online",true);assertFalse(Undetect.interactive("interactive",true));
  assertFalse(Undetect.interactive("interactive",false));assertTrue(Undetect.interactive("other",true));
 }
 @Test public void notificationRedactionRemovesHistoryPeopleAndCustomLayout() {
  Notification n=new Notification();n.extras=new Bundle();n.extras.putString("android.text","secret");n.extras.putString("android.messages","secret history");
  n.tickerText="private";n.contentView=new RemoteViews("app.maxund",123);
  Undetect.notification(n);assertEquals("secret",n.extras.getString("android.text"));
  Undetect.set("notifications",true);n=Undetect.notification(n);
  assertFalse(n.extras.containsKey("android.messages"));assertNull(n.contentView);assertNull(n.actions);
  assertEquals("Новое уведомление",n.extras.getCharSequence(Notification.EXTRA_TEXT));assertEquals(Notification.VISIBILITY_SECRET,n.visibility);
 }
 enum Place { AUTOLOAD,CHAT,CHAT_MEDIA }
 @Test public void autoloadGuardPreservesManualDownloads() {
  Undetect.set("autoload",true);assertTrue(Undetect.blockAutoload(Place.AUTOLOAD));
  assertFalse(Undetect.blockAutoload(Place.CHAT));assertFalse(Undetect.blockAutoload(Place.CHAT_MEDIA));assertFalse(Undetect.blockAutoload(null));
 }
 @Test public void settingsWrapperPreservesOriginalViewAndOpensUndetect() {
  Activity a=Robolectric.buildActivity(Activity.class).setup().get();View original=new View(a);
  LinearLayout wrapper=(LinearLayout)Undetect.settings(original);assertSame(original,wrapper.getChildAt(1));
  wrapper.getChildAt(0).performClick();assertEquals(UndetectActivity.class.getName(),Shadows.shadowOf(a).getNextStartedActivity().getComponent().getClassName());
 }
 @Test public void settingsScreenBuildsAndKeepsNewOptionsOff() {
  Activity a=Robolectric.buildActivity(UndetectActivity.class).setup().get();
  assertTrue((a.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE)!=0);
  assertFalse(Undetect.enabled("incognito"));assertFalse(Undetect.enabled("lock"));
 }
}
