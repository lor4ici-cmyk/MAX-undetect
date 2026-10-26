from pathlib import Path
import re,sys,xml.etree.ElementTree as E
root=Path(sys.argv[1]); A='{http://schemas.android.com/apk/res/android}'
manifest=root/'AndroidManifest.xml'; tree=E.parse(manifest); doc=tree.getroot()
E.register_namespace('android', A[1:-1])
removed=[]
blocked={
 'android.permission.ACCESS_FINE_LOCATION','android.permission.ACCESS_COARSE_LOCATION',
 'android.permission.READ_CONTACTS','android.permission.WRITE_CONTACTS',
 'android.permission.READ_MEDIA_AUDIO','android.permission.READ_MEDIA_IMAGES','android.permission.READ_MEDIA_VIDEO',
 'android.permission.READ_EXTERNAL_STORAGE','android.permission.WRITE_EXTERNAL_STORAGE',
 'android.permission.RECEIVE_BOOT_COMPLETED','android.permission.REQUEST_INSTALL_PACKAGES',
 'android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS','android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION',
 'com.google.android.gms.permission.AD_ID','com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE',
}
for element in list(doc):
 if element.tag=='uses-permission' and element.get(A+'name') in blocked:
  removed.append(element.get(A+'name'));doc.remove(element)
app=doc.find('application');app.set(A+'allowBackup','false');app.set(A+'fullBackupContent','false')
for e in list(app):
 name=e.get(A+'name','');authority=e.get(A+'authorities','')
 if authority.endswith('.tracer-startup') or name.startswith('ru.ok.tracer.startup.Initializer@'):
  app.remove(e)
 elif e.tag=='receiver' and ('Boot' in name or 'boot' in name):e.set(A+'enabled','false')
 if e.get(A+'foregroundServiceType'):
  types=e.get(A+'foregroundServiceType').split('|')
  e.set(A+'foregroundServiceType','|'.join(t for t in types if t not in ('location','mediaProjection')))
tree.write(manifest,encoding='utf-8',xml_declaration=True)
for f in root.glob('res/values*/strings.xml'):
 s=f.read_text();s=re.sub(r'(<string name="oneme_app_name">).*?(</string>)',r'\1MAXund Native\2',s,flags=re.S);f.write_text(s)
# Disable known tracker entrypoints rather than merely redirecting their packets.
f=root/'smali/com/my/tracker/MyTracker.smali';s=f.read_text()
for signature in ['initTracker(Ljava/lang/String;Landroid/app/Application;)V','trackEvent(Ljava/lang/String;)V','trackEvent(Ljava/lang/String;Ljava/util/Map;)V','flush()V']:
 pattern=r'(?m)^\.method public static '+re.escape(signature)+r'\n.*?^\.end method'
 s,n=re.subn(pattern,'.method public static '+signature+'\n    .locals 0\n    return-void\n.end method',s,flags=re.S|re.M)
 assert n==1,signature
f.write_text(s)
# Permission-status analytics worker returns success without collecting permissions.
f=root/'smali/one/me/android/DailyAnalyticsWorker.smali';s=f.read_text()
s,n=re.subn(r'(?ms)^\.method public final d\(\)Lov9;\n.*?^\.end method', '.method public final d()Lov9;\n    .locals 1\n    new-instance v0, Lnv9;\n    invoke-direct {v0}, Lnv9;-><init>()V\n    return-object v0\n.end method',s);assert n==1;f.write_text(s)
f=root/'smali/one/me/android/OneMeApplication.smali';s=f.read_text();needle='    invoke-super {v0}, Landroid/app/Application;->onCreate()V';assert s.count(needle)==1
s=s.replace(needle,needle+'\n\n    invoke-static/range {p0 .. p0}, Lapp/maxund/privacy/PrivacyLifecycle;->install(Landroid/app/Application;)V');f.write_text(s)
# Replace only literal analytics endpoint hosts, never authentication/message endpoints.
hosts=['sdk-api.apptracer.ru','tracker-api.vk-analytics.ru','api.ipify.org','ifconfig.me','ip.mail.ru','ipv6-internet.yandex.net','ipv4-internet.yandex.net','checkip.amazonaws.com']
changed=0
for f in root.glob('smali*/**/*.smali'):
 s=f.read_text();new=s
 for host in hosts:new=new.replace(host,'blocked.maxund.invalid')
 if new!=s:f.write_text(new);changed+=1
(root/'assets/maxund').mkdir(parents=True,exist_ok=True)
(root/'assets/maxund/changes.txt').write_text('MAXund based on MIN Safe MAX ad2568aba95a16fa140713a7ae273dc7e4b004d6.\nOriginal authentication, UI and Digital ID code retained.\nMyTracker entrypoints and DailyAnalyticsWorker disabled; Tracer providers removed.\nContacts/location/media-library/advertising-id/autostart/screen-sharing permissions removed.\nCAMERA/RECORD_AUDIO retained; Android 13+ media grants scheduled for revocation when app backgrounds, ending its process.\nThe mod is not certified free of all tracking. Server data collection remains.\n')
print('Removed permission declarations:',len(removed),'Endpoint literal files patched:',changed)

f=root/'apktool.yml';s=f.read_text();assert 'minSdkVersion: 26' in s;f.write_text(s.replace('minSdkVersion: 26','minSdkVersion: 33'))

# A removed installer permission must also disable its eager capability query.
# Android throws SecurityException for this query without the declaration.
queries = 0
for f in root.glob('smali*/**/*.smali'):
 s=f.read_text()
 s,n=re.subn(r'invoke-virtual \{[^}]+\}, Landroid/content/pm/PackageManager;->canRequestPackageInstalls\(\)Z\s+move-result ([vp][0-9]+)', r'const/4 \1, 0x0',s)
 if n:f.write_text(s);queries+=n
assert queries==3, 'Upstream install capability query layout changed'
print('Disabled installer capability queries:',queries)
