#!/usr/bin/env python3
"""Root-only smoke of the private activity in the dedicated account-free MAXund AVD."""
import sys,json,hashlib,time,re
from pathlib import Path
from adb_shell.adb_device import AdbDeviceTcp
root=Path(__file__).resolve().parent
x=AdbDeviceTcp('127.0.0.1',int(sys.argv[1]) if len(sys.argv)>1 else 5581,default_transport_timeout_s=180);x.connect()
def shell(cmd):return x.shell(cmd,read_timeout_s=180,timeout_s=240)
r={'version':'26.34.1-undetect.2','real_account_used':False,'apk_sha256':hashlib.sha256((root/'work/MAXund-native.apk').read_bytes()).hexdigest()}
try:
 assert shell('getprop ro.kernel.qemu').strip()=='1'
 assert shell('getprop ro.boot.qemu.avd_name').strip()=='MAXund'
 path=shell('pm path ru.oneme.app').strip().removeprefix('package:')
 assert path.startswith('/data/app/') and '\n' not in path
 assert shell('sha256sum '+path).split()[0]==r['apk_sha256']
 assert 'uid=0' in shell('su 0 id')
 shell('logcat -b crash -c')
 r['launch']=shell('su 0 am start -W -n ru.oneme.app/app.maxund.privacy.UndetectActivity')
 for _ in range(15):
  top=shell('dumpsys activity top')
  bounds=re.findall(r'android.widget.Switch\{[^}]*? (\d+),(\d+)-(\d+),(\d+)',top)
  if top.count('android.widget.Switch{')==9 and any(int(c)>int(a) and int(d)>int(b) for a,b,c,d in bounds):break
  time.sleep(3)
 r['measured_switch_present']=any(int(c)>int(a) and int(d)>int(b) for a,b,c,d in bounds)
 r['activity_present']='app.maxund.privacy.UndetectActivity' in top
 r['switch_count']=top.count('android.widget.Switch{')
 r['fresh_crash_log_empty']=not shell('logcat -d -b crash').strip()
 assert r['activity_present'] and r['switch_count']==9 and r['measured_switch_present'] and r['fresh_crash_log_empty'],r
 r['result']='passed'
 print(json.dumps(r,ensure_ascii=False,indent=2),flush=True)
finally:
 (root/'work/undetect-settings-smoke.json').write_text(json.dumps(r,ensure_ascii=False,indent=2)+'\n');x.close()
