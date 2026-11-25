#!/usr/bin/env python3
"""Build do app sem Gradle: aapt2 + javac + d8 + zipalign + apksigner."""

import glob
import os
import shutil
import subprocess
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = '/workspace/project/apks/tools'
BUILD_TOOLS = os.path.join(TOOLS, 'android-14')
ANDROID_JAR = os.path.join(TOOLS, 'android-34', 'android.jar')
_JDK_CANDIDATES = [
    '/workspace/project/tools/jdk-17.0.21.1+2',
    '/home/openhands/.jdk/jdk-17.0.21.1+2',
]
JAVA_HOME = os.environ.get('JAVA_HOME') or next(
    (path for path in _JDK_CANDIDATES if os.path.isdir(os.path.join(path, 'bin'))),
    _JDK_CANDIDATES[0],
)
JAVA = os.path.join(JAVA_HOME, 'bin', 'java')
JAVAC = os.path.join(JAVA_HOME, 'bin', 'javac')
KEYTOOL = os.path.join(JAVA_HOME, 'bin', 'keytool')

AAPT2 = os.path.join(BUILD_TOOLS, 'aapt2')
D8 = os.path.join(BUILD_TOOLS, 'd8')
ZIPALIGN = os.path.join(BUILD_TOOLS, 'zipalign')
APKSIGNER = os.path.join(BUILD_TOOLS, 'apksigner')

OUT = os.path.join(HERE, 'apk')
WORK = os.path.join(HERE, '.build')
_KEYSTORE_CANDIDATES = [
    os.environ.get('BYPASS_KEYSTORE') or '',
    os.path.join(HERE, 'release.keystore'),
    os.path.join(HERE, '..', 'keys', 'bypass.keystore'),
    '/workspace/project/keys/bypass.keystore',
]
KEYSTORE = next((path for path in _KEYSTORE_CANDIDATES if path and os.path.exists(path)),
                os.path.join(HERE, 'release.keystore'))


def run(cmd):
    print('  $', ' '.join(os.path.basename(part) if os.sep in part else part for part in cmd))
    env = dict(os.environ)
    env['JAVA_HOME'] = JAVA_HOME
    env['PATH'] = os.path.join(JAVA_HOME, 'bin') + os.pathsep + env.get('PATH', '')
    result = subprocess.run(cmd, capture_output=True, text=True, env=env)
    if result.returncode != 0:
        sys.stderr.write(result.stdout[-6000:])
        sys.stderr.write(result.stderr[-6000:])
        raise SystemExit('falhou: ' + ' '.join(cmd[:3]))
    return result.stdout


def main():
    os.makedirs(OUT, exist_ok=True)
    shutil.rmtree(WORK, ignore_errors=True)
    compiled = os.path.join(WORK, 'res.zip')
    classes = os.path.join(WORK, 'classes')
    dex_dir = os.path.join(WORK, 'dex')
    os.makedirs(classes)
    os.makedirs(dex_dir)

    print('[1/6] compilando recursos')
    run([AAPT2, 'compile', '--dir', os.path.join(HERE, 'res'), '-o', compiled])

    print('[2/6] linkando base')
    unsigned = os.path.join(WORK, 'base-unsigned.apk')
    generated = os.path.join(WORK, 'gen')
    os.makedirs(generated)
    run([AAPT2, 'link', '-o', unsigned, '-I', ANDROID_JAR,
         '--manifest', os.path.join(HERE, 'AndroidManifest.xml'),
         '-R', compiled, '--min-sdk-version', '24', '--target-sdk-version', '34',
         '--version-code', '20', '--version-name', '3.6', '--auto-add-overlay',
         '--java', generated])

    print('[3/6] compilando java')
    sources = glob.glob(os.path.join(HERE, 'java', '**', '*.java'), recursive=True)
    sources += glob.glob(os.path.join(generated, '**', '*.java'), recursive=True)
    run([JAVAC, '--release', '8', '-encoding', 'UTF-8', '-classpath', ANDROID_JAR, '-d', classes] + sources)

    print('[4/6] dexando')
    class_files = glob.glob(os.path.join(classes, '**', '*.class'), recursive=True)
    run([D8, '--lib', ANDROID_JAR, '--min-api', '24', '--output', dex_dir] + class_files)

    print('[5/6] empacotando')
    with zipfile.ZipFile(unsigned, 'a', zipfile.ZIP_DEFLATED) as archive:
        archive.write(os.path.join(dex_dir, 'classes.dex'), 'classes.dex')

    if not os.path.exists(KEYSTORE):
        run([KEYTOOL, '-genkeypair', '-keystore', KEYSTORE, '-alias', 'release',
             '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000',
             '-storepass', 'bypass123', '-keypass', 'bypass123',
             '-dname', 'CN=Bypass, O=Local, C=BR'])

    aligned = os.path.join(WORK, 'aligned.apk')
    run([ZIPALIGN, '-f', '4', unsigned, aligned])

    print('[6/6] assinando')
    final = os.path.join(OUT, 'Bypass-3.6.apk')
    run([APKSIGNER, 'sign', '--ks', KEYSTORE, '--ks-pass', 'pass:bypass123',
         '--key-pass', 'pass:bypass123', '--ks-key-alias', 'release',
         '--min-sdk-version', '24', '--v4-signing-enabled', 'false',
         '--out', final, aligned])

    print('\napk:', final, os.path.getsize(final), 'bytes')
    print(run([APKSIGNER, 'verify', '--print-certs', final]))


if __name__ == '__main__':
    main()
