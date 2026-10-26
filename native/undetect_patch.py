"""Version-specific hooks; refuse silently missing or duplicate insertion points."""
from pathlib import Path
import re,sys,xml.etree.ElementTree as E
root=Path(sys.argv[1]);A='{http://schemas.android.com/apk/res/android}'
def replace(path,old,new):
 f=root/path;s=f.read_text();assert s.count(old)==1,(path,old);f.write_text(s.replace(old,new))
def prepend(path,signature,locals_count,code):
 old=signature+'\n    .locals '+str(locals_count)+'\n'
 replace(path,old,old+'\n'+code+'\n')
replace('apktool.yml','versionCode: 6856','versionCode: 6857')
replace('apktool.yml','versionName: 26.34.1','versionName: 26.34.1-undetect.2')
f=root/'AndroidManifest.xml';tree=E.parse(f);app=tree.getroot().find('application')
for name in ['UndetectActivity','UndetectLockActivity']:
 E.SubElement(app,'activity',{A+'name':'app.maxund.privacy.'+name,A+'exported':'false',A+'theme':'@android:style/Theme.Material.NoActionBar',A+'excludeFromRecents':'true'})
tree.write(f,encoding='utf-8',xml_declaration=True)
replace('smali_classes3/one/me/settings/SettingsListScreen.smali','    return-object p2\n.end method', '''    invoke-static {p2}, Lapp/maxund/privacy/Undetect;->settings(Landroid/view/View;)Landroid/view/View;
    move-result-object p2
    return-object p2
.end method''')
prepend('smali/oyi.smali','.method public final a(Ljava/lang/String;Z)V',0,'''    invoke-static {p1, p2}, Lapp/maxund/privacy/Undetect;->interactive(Ljava/lang/String;Z)Z
    move-result p2''')
prepend('smali/q7c.smali','.method public final g(Loyi;ZJLyxi;)V',8,'''    invoke-static {p0, p1, p5}, Lapp/maxund/privacy/Undetect;->beforeRequest(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z
    move-result v0
    if-eqz v0, :undetect_send
    return-void
    :undetect_send''')
prepend('smali/ytc.smali','.method public final h(Ldk5;Lv7;)V',1,'''    invoke-static {p0, p1, p2}, Lapp/maxund/privacy/Undetect;->confirmCall(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z
    move-result v0
    if-eqz v0, :undetect_route
    return-void
    :undetect_route''')
prepend('smali/ru/ok/tamtam/upload/workers/DownloadAttachesWorker.smali','.method public final k(Lw15;)Ljava/lang/Object;',4,'''    iget-object v0, p0, Lru/ok/tamtam/upload/workers/DownloadAttachesWorker;->D:Ly66;
    invoke-static {v0}, Lapp/maxund/privacy/Undetect;->blockAutoload(Ljava/lang/Object;)Z
    move-result v0
    if-eqz v0, :undetect_download
    new-instance v0, Lnv9;
    invoke-direct {v0}, Lnv9;-><init>()V
    return-object v0
    :undetect_download''')
count=0
for f in root.glob('smali*/**/*.smali'):
 s=f.read_text()
 pattern=r'(?m)^(    invoke-virtual \{([^}]+)\}, Landroid/app/NotificationManager;->notify\([^\n]+)$'
 def insert(m):
  global count
  reg=m[2].split(', ')[-1];count+=1
  return '    invoke-static/range {'+reg+' .. '+reg+'}, Lapp/maxund/privacy/Undetect;->notification(Landroid/app/Notification;)Landroid/app/Notification;\n    move-result-object '+reg+'\n\n'+m[1]
 s,n=re.subn(pattern,insert,s)
 if n:f.write_text(s)
assert count==6,count
print('Undetect hooks applied; notification paths:',count)
