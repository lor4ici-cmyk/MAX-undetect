#!/usr/bin/env python3
"""Verify the assembled APK rather than trusting source-side patch messages."""
from pathlib import Path
import hashlib,re,struct,subprocess,zipfile,zlib,json
ROOT=Path(__file__).resolve().parent;APK=ROOT/'work/MAXund-native.apk';BT=Path('/workspace/toolchains/android-sdk/build-tools/35.0.0')
def tool(*args):return subprocess.check_output([str(BT/'aapt2'),*map(str,args)],text=True)
badging=tool('dump','badging',APK);permissions=tool('dump','permissions',APK)
assert "package: name='ru.oneme.app'" in badging
assert "sdkVersion:'33'" in badging or "minSdkVersion:'33'" in badging
assert "application-label:'MAXund Native'" in badging
names=set(re.findall(r"uses-permission: name='([^']+)'",permissions))
for name in ['READ_CONTACTS','WRITE_CONTACTS','ACCESS_FINE_LOCATION','ACCESS_COARSE_LOCATION','READ_EXTERNAL_STORAGE','WRITE_EXTERNAL_STORAGE','READ_MEDIA_IMAGES','READ_MEDIA_VIDEO','READ_MEDIA_AUDIO','RECEIVE_BOOT_COMPLETED','REQUEST_INSTALL_PACKAGES','REQUEST_IGNORE_BATTERY_OPTIMIZATIONS','FOREGROUND_SERVICE_MEDIA_PROJECTION']:
 assert 'android.permission.'+name not in names,name
assert 'com.google.android.gms.permission.AD_ID' not in names
assert {'android.permission.CAMERA','android.permission.RECORD_AUDIO'} <= names
xml=tool('dump','xmltree',APK,'--file','AndroidManifest.xml')
assert 'tracer-startup' not in xml
assert 'ru.ok.tracer.startup.Initializer@' not in xml

def uleb(data,offset):
 value=0;shift=0
 while True:
  byte=data[offset];offset+=1;value|=(byte&127)<<shift
  if byte<128:return value,offset
  shift+=7

def methods(data):
 string_count,string_off=struct.unpack_from('<II',data,56)
 strings=[]
 for i in range(string_count):
  off=struct.unpack_from('<I',data,string_off+i*4)[0];_,off=uleb(data,off)
  end=data.index(0,off);strings.append(data[off:end].decode('utf-8','replace'))
 type_count,type_off=struct.unpack_from('<II',data,64)
 types=[strings[struct.unpack_from('<I',data,type_off+i*4)[0]] for i in range(type_count)]
 count,method_off=struct.unpack_from('<II',data,88)
 ids=[]
 for i in range(count):
  class_idx,proto,name=struct.unpack_from('<HHI',data,method_off+i*8);ids.append((types[class_idx],strings[name]))
 class_count,class_off=struct.unpack_from('<II',data,96)
 result={}
 for i in range(class_count):
  fields=struct.unpack_from('<8I',data,class_off+i*32);p=fields[6]
  if not p:continue
  counts=[]
  for _ in range(4):n,p=uleb(data,p);counts.append(n)
  for _ in range(counts[0]+counts[1]):_,p=uleb(data,p);_,p=uleb(data,p)
  for number in counts[2:]:
   index=0
   for _ in range(number):
    diff,p=uleb(data,p);index+=diff;_,p=uleb(data,p);code,p=uleb(data,p)
    if code:
     length=struct.unpack_from('<I',data,code+12)[0]
     result.setdefault(ids[index],[]).append(data[code+16:code+16+length*2])
 return ids,result
all_methods={};hook=False;dex_count=0
with zipfile.ZipFile(APK) as archive:
 for name in archive.namelist():
  if re.fullmatch(r'classes\d*\.dex',name):
   data=archive.read(name);dex_count+=1
   assert data[12:32]==hashlib.sha1(data[32:]).digest(),name+' SHA1'
   assert struct.unpack_from('<I',data,8)[0]==zlib.adler32(data[12:])&0xffffffff,name+' Adler32'
   ids,items=methods(data);all_methods.update(items)
   assert ('Landroid/content/pm/PackageManager;','canRequestPackageInstalls') not in ids,'Removed permission still queried by compiled code'
   for index,item in enumerate(ids):
    if item==('Lapp/maxund/privacy/PrivacyLifecycle;','install'):
     for code in items.get(('Lone/me/android/OneMeApplication;','onCreate'),[]):
      if b'\x77\x01'+struct.pack('<H',index) in code:hook=True
 assert 'assets/maxund/MIN-LICENSE' in archive.namelist()
for name in ['initTracker','trackEvent','flush']:
 implementations=all_methods[('Lcom/my/tracker/MyTracker;',name)]
 assert implementations and all(code==b'\x0e\x00' for code in implementations),name
worker=all_methods[('Lone/me/android/DailyAnalyticsWorker;','d')]
assert len(worker)==1 and len(worker[0])<=16,'Daily analytics worker must only construct success'
assert ('Lapp/maxund/privacy/PrivacyLifecycle;','run') in all_methods
assert hook,'Application must register privacy lifecycle'
report={'apk':str(APK),'sha256':hashlib.sha256(APK.read_bytes()).hexdigest(),'dex_files_verified':dex_count,'min_sdk':33,'package':'ru.oneme.app','permissions':sorted(names),'checks':'manifest, internal DEX integrity, compiled MyTracker no-ops, worker, lifecycle registration passed','live_login':'not verified'}
(ROOT/'work/audit.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({k:v for k,v in report.items() if k!='permissions'},ensure_ascii=False,indent=2))
