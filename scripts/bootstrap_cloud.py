#!/usr/bin/env python3
"""Recreate pinned Linux cloud build tools using verified upstream archives.

Installing Android SDK packages implies acceptance of the Android SDK license:
https://developer.android.com/studio/terms
No authentication or application credentials are needed.
"""
from pathlib import Path
import hashlib
import os
import shutil
import tarfile
import urllib.parse
import urllib.request
import zipfile

ROOT = Path('/workspace/toolchains')
ROOT.mkdir(parents=True, exist_ok=True)


def archive(name, url, expected, algorithm='sha256'):
    target = ROOT / name
    if not target.exists():
        temporary = target.with_suffix(target.suffix + '.partial')
        with urllib.request.urlopen(url, timeout=120) as response, temporary.open('wb') as out:
            shutil.copyfileobj(response, out)
        temporary.replace(target)
    with target.open('rb') as stream:
        actual = hashlib.file_digest(stream, algorithm).hexdigest()
    if actual != expected:
        raise RuntimeError(f'Checksum mismatch for {name}; refusing to use archive')
    return target


gradle = archive('gradle.zip', 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip',
                 'f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6')
if not (ROOT / 'gradle-8.11.1/bin/gradle').exists():
    with zipfile.ZipFile(gradle) as z:
        z.extractall(ROOT)
(ROOT / 'gradle-8.11.1/bin/gradle').chmod(0o755)

jdk = archive('jdk.tar.gz', 'https://github.com/adoptium/temurin17-binaries/releases/download/'
              'jdk-17.0.21%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.21_9.tar.gz',
              '8b9b611d917e3dfd8419d3566b5949226f69b052fed7e109c1368add1dfe8711')
if not (ROOT / 'jdk-17.0.21+9/bin/javac').exists():
    with tarfile.open(jdk) as t:
        t.extractall(ROOT, filter='data')

sdk = ROOT / 'android-sdk'
packages = [
    ('platform-35_r02.zip', '0bb560a90a7a2cbd0dd8348224d518b638fe7949', 'platforms/android-35'),
    ('build-tools_r35_linux.zip', '2cfaa0bbb2336e9ec18ed3ecea84fa2e2af607bc', 'build-tools/35.0.0'),
    ('platform-tools_r37.0.1-linux.zip', '477254aa5f903c15cf51001717bdf347fb6b53e0', 'platform-tools'),
]
for name, checksum, location in packages:
    package = archive(name, 'https://dl.google.com/android/repository/' + name, checksum, 'sha1')
    destination = sdk / location
    if destination.exists():
        continue
    staging = ROOT / ('extract-' + name)
    staging.mkdir(exist_ok=True)
    with zipfile.ZipFile(package) as z:
        z.extractall(staging)
        for info in z.infolist():
            mode = info.external_attr >> 16
            if mode:
                (staging / info.filename).chmod(mode)
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.move(str(next(staging.iterdir())), str(destination))
    staging.rmdir()

(sdk / 'licenses').mkdir(exist_ok=True)
(sdk / 'licenses/android-sdk-license').write_text('9002c006f4b8d9a16e715a9fa4df30ddb8abf9d9\n')
(ROOT / 'android-user').mkdir(exist_ok=True)
gradle_home = ROOT / 'gradle-home'
gradle_home.mkdir(exist_ok=True)
properties = gradle_home / 'gradle.properties'
# Only update our supported machine-specific keys; do not overwrite unrelated settings.
settings = {}
proxy = urllib.parse.urlparse(os.environ.get('HTTPS_PROXY', ''))
if proxy.hostname:
    for scheme in ('http', 'https'):
        settings[f'systemProp.{scheme}.proxyHost'] = proxy.hostname
        settings[f'systemProp.{scheme}.proxyPort'] = str(proxy.port or 80)
truststore = Path('/etc/ssl/certs/java/cacerts')
if truststore.exists():
    settings['systemProp.javax.net.ssl.trustStore'] = str(truststore)
lines = properties.read_text().splitlines() if properties.exists() else []
lines = [line for line in lines if line.partition('=')[0] not in settings]
lines.extend(key + '=' + value for key, value in settings.items())
properties.write_text('\n'.join(lines) + '\n')
checkout = Path(__file__).resolve().parent.parent
local = checkout / 'local.properties'
if not local.exists():
    local.write_text('sdk.dir=' + str(sdk) + '\n')
print('Pinned JDK, Gradle and Android SDK verified; cloud build configuration prepared.')
