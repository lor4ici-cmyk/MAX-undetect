#!/usr/bin/env python3
"""For an existing clean, local Android 13 test guest; never uses an account.
Requires adb-shell (installed separately) and a guest ADB TCP port on localhost.
Run only on a disposable emulator: this installs/updates ru.oneme.app and grants
camera/microphone solely to test their subsequent revocation.
"""
from pathlib import Path
import sys,time,json,hashlib
from adb_shell.adb_device import AdbDeviceTcp
root=Path(__file__).resolve().parent;apk=root/'work/MAXund-native.apk'
port=int(sys.argv[1]) if len(sys.argv)>1 else 5581
x=AdbDeviceTcp('127.0.0.1',port,default_transport_timeout_s=180);x.connect()
def shell(cmd):return x.shell(cmd,read_timeout_s=180,timeout_s=240)
report={'apk_sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'test_account_used':False}
try:
 if shell('getprop ro.kernel.qemu').strip()!='1':raise RuntimeError('Refusing to test on a non-emulator device')
 if '--fresh' in sys.argv[2:]:
  if shell('getprop ro.boot.qemu.avd_name').strip()!='MAXund':raise RuntimeError('Fresh reset allowed only for the dedicated MAXund fixture')
  print('Removing earlier account-free test installation from the dedicated fixture...',flush=True)
  print(shell('pm uninstall ru.oneme.app'),flush=True)
 if '--skip-install' in sys.argv[2:]:
  path=shell('pm path ru.oneme.app').strip().removeprefix('package:')
  if not path.startswith('/data/app/') or '\n' in path:raise RuntimeError('Unexpected installed APK path')
  digest=shell('sha256sum '+path).split()[0]
  if digest!=report['apk_sha256']:raise RuntimeError('Installed APK does not match the exact audited APK')
  report['install']='existing exact APK verified by SHA-256'
 else:
  print('Installing final audited APK in disposable emulator...',flush=True)
  x.push(str(apk),'/data/local/tmp/MAXund-final.apk',read_timeout_s=180)
  installed=x.shell('pm install -r /data/local/tmp/MAXund-final.apk',read_timeout_s=600,timeout_s=660,transport_timeout_s=600);print(installed,flush=True)
  if 'Success' not in installed:raise RuntimeError('Guest installation failed')
  report['install']=installed.strip()
 shell('logcat -b crash -c')
 report['launch_command']=shell('am start -W -n ru.oneme.app/one.me.android.MainActivity')
 print(report['launch_command'],flush=True)
 top=shell('dumpsys activity top')
 report['native_phone_screen_created']='oneme_login_input' in top
 report['process_started']=bool(shell('pidof ru.oneme.app').strip())
 crash=shell('logcat -d -b crash')
 report['fresh_crash_log_empty']=not crash.strip()
 if not report['process_started'] or not report['fresh_crash_log_empty']:raise RuntimeError('App failed startup')
 if not report['native_phone_screen_created']:
  # A first load on a software emulator can complete after am's wait deadline.
  time.sleep(5);top=shell('dumpsys activity top');report['native_phone_screen_created']='oneme_login_input' in top
 if not report['native_phone_screen_created']:raise RuntimeError('Native phone input screen not created')
 print('Native phone screen created without a crash. Testing background revocation...',flush=True)
 shell('pm grant ru.oneme.app android.permission.CAMERA; pm grant ru.oneme.app android.permission.RECORD_AUDIO')
 report['permissions_before_background']=shell('dumpsys package ru.oneme.app | grep -E "CAMERA:|RECORD_AUDIO:"')
 if report['permissions_before_background'].count('granted=true')!=2:raise RuntimeError('Could not set up media-grant fixture')
 shell('input keyevent 3')
 report['permissions_after_background']=''
 for attempt in range(20):
  time.sleep(3)
  state=shell('dumpsys package ru.oneme.app | grep -E "CAMERA:|RECORD_AUDIO:"')
  process=shell('pidof ru.oneme.app').strip()
  print('Revocation check',attempt+1,repr(state.strip()),'process alive:',bool(process),flush=True)
  report['permissions_after_background']=state;report['process_alive_after_background']=bool(process)
  if state.count('granted=false')==2 and not process:break
 else:raise RuntimeError('Background revocation did not finish within test deadline')
 report['result']='passed'
finally:
 (root/'work/emulator-smoke.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
 x.close()
