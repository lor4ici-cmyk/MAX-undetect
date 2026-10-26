#!/usr/bin/env python3
from pathlib import Path
import hashlib,urllib.request,tarfile,subprocess,shutil,os
ROOT=Path(__file__).resolve().parent;TOOLS=Path('/workspace/toolchains');WORK=ROOT/'work';WORK.mkdir(exist_ok=True)
JDK=TOOLS/'jdk-17.0.21+9';SDK=TOOLS/'android-sdk';BT=SDK/'build-tools/35.0.0';APKTOOL=TOOLS/'apktool_2.12.0.jar'
COMMIT='ad2568aba95a16fa140713a7ae273dc7e4b004d6'
def verify(file,url,sha):
 if not file.exists():urllib.request.urlretrieve(url,file)
 if hashlib.sha256(file.read_bytes()).hexdigest()!=sha:raise RuntimeError('Untrusted archive '+str(file))
verify(APKTOOL,'https://github.com/iBotPeaches/Apktool/releases/download/v2.12.0/apktool_2.12.0.jar','effb69dab2f93806cafc0d232f6be32c2551b8d51c67650f575e46c016908fdd')
archive=TOOLS/'min-safe-max-ad2568.tar.gz'
verify(archive,'https://codeload.github.com/Rise0x00/MIN-Safe-MAX/tar.gz/'+COMMIT,'a579db6cab855d58a8717c57fae22eb63774fd7d3271a9deccc16ebe801346de')
def run(args):subprocess.run([str(a) for a in args],check=True)
for filename in ['unsigned.apk','aligned.apk','MAXund-native.apk']:
 (WORK/filename).unlink(missing_ok=True)
base=WORK/'base'
if base.exists():shutil.rmtree(base)
base.mkdir()
with tarfile.open(archive) as t:t.extractall(base,filter='data')
source=next(base.iterdir())/'min_smali';out=WORK/'source'
if out.exists():shutil.rmtree(out)
shutil.copytree(source,out,ignore=shutil.ignore_patterns('build'))
run(['python3',ROOT/'patch.py',out])
run(['python3',ROOT/'undetect_patch.py',out])
classes=WORK/'helper-classes';classes.mkdir(exist_ok=True);dex=WORK/'helper-dex';dex.mkdir(exist_ok=True)
run([JDK/'bin/javac','-source','8','-target','8','-classpath',SDK/'platforms/android-35/android.jar','-d',classes,*(ROOT/'privacy').glob('*.java')])
run([JDK/'bin/java','-cp',BT/'lib/d8.jar','com.android.tools.r8.D8','--min-api','26','--lib',SDK/'platforms/android-35/android.jar','--output',dex,*(classes/'app/maxund/privacy').rglob('*.class')])
run([JDK/'bin/javac','-cp',APKTOOL,'-d',classes,ROOT/'tools/Decode.java'])
# Keep privacy helper in its own dex to avoid main dex method limits.
run([JDK/'bin/java','-cp',str(classes)+':'+str(APKTOOL),'Decode',dex/'classes.dex',out/'smali_classes4'])
shutil.copyfile(next(base.iterdir())/'LICENSE',out/'assets/maxund/MIN-LICENSE')
run([JDK/'bin/java','-Xmx4g','-jar',APKTOOL,'b',out,'-p',TOOLS/'apktool-framework','-j','2','-o',WORK/'unsigned.apk'])
run([BT/'zipalign','-P','16','-f','4',WORK/'unsigned.apk',WORK/'aligned.apk'])
key=TOOLS/'android-user/debug.keystore'
if not key.exists():
 key.parent.mkdir(parents=True,exist_ok=True)
 run([JDK/'bin/keytool','-genkeypair','-keystore',key,'-storepass','android','-keypass','android','-alias','androiddebugkey','-dname','CN=MAXund Development,O=Android,C=US','-keyalg','RSA','-keysize','2048','-validity','10000'])
environ=os.environ.copy();environ['JAVA_HOME']=str(JDK);environ['PATH']=str(JDK/'bin')+':'+environ['PATH']
subprocess.run([str(BT/'apksigner'),'sign','--ks',str(key),'--ks-pass','pass:android','--key-pass','pass:android','--out',str(WORK/'MAXund-native.apk'),str(WORK/'aligned.apk')],env=environ,check=True)
subprocess.run([str(BT/'apksigner'),'verify','--verbose',str(WORK/'MAXund-native.apk')],env=environ,check=True)
run([BT/'zipalign','-c','-P','16','4',WORK/'MAXund-native.apk'])
print('Native APK SHA-256:',hashlib.sha256((WORK/'MAXund-native.apk').read_bytes()).hexdigest())
