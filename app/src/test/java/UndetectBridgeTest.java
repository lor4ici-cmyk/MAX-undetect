import app.maxund.privacy.Undetect;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=33)
public class UndetectBridgeTest {
 @Test public void suppressedRequestsCompleteLocallyAndCanBeManuallyQueued() throws Exception {
  android.app.Application app=RuntimeEnvironment.getApplication();Undetect.install(app);
  app.getSharedPreferences("maxund_undetect",0).edit().clear().putBoolean("reads",true).commit();
  q7c sender=new q7c();oyi req=new oyi();req.opcode=50;req.a.put("chatId",123L);req.a.put("type","READ_MESSAGE");
  final String[] error={null};yxi cb=new yxi(){public void g(fyi e){error[0]=e.code;} public void b(Object x){}public long p(){return 0;}};
  assertTrue(Undetect.beforeRequest(sender,req,cb));assertEquals("undetect.suppressed",error[0]);assertEquals(0,sender.sends);
  var manual=Undetect.class.getDeclaredMethod("sendRead",String.class);manual.setAccessible(true);
  assertEquals(true,manual.invoke(null,"123"));assertEquals(1,sender.sends);
  assertEquals(false,manual.invoke(null,"123"));
  req.opcode=64;assertFalse(Undetect.beforeRequest(sender,req,cb));
 }

 @Test public void outgoingCallWaitsForExplicitConfirmationAndCancelDoesNotStart() throws Exception {
  android.app.Application app=RuntimeEnvironment.getApplication();Undetect.install(app);
  app.getSharedPreferences("maxund_undetect",0).edit().clear().putBoolean("calls",true).commit();
  android.app.Activity a=Robolectric.buildActivity(android.app.Activity.class).setup().get();
  var resume=Undetect.class.getDeclaredMethod("resume",android.app.Activity.class);resume.setAccessible(true);resume.invoke(null,a);
  ytc router=new ytc();dk5 route=new dk5(new cl1());
  assertTrue(Undetect.confirmCall(router,route,new v7()));
  Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
  org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick();
  Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
  assertEquals(0,router.starts);
  assertTrue(Undetect.confirmCall(router,new dk5(new dl1()),new v7()));
  Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
  org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
  Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
  assertEquals(1,router.starts);
  assertFalse(Undetect.confirmCall(router,new dk5(new Object()),new v7()));
 }
}
