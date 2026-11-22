#!/usr/bin/env python3
"""截图取字 · 无 Gradle 构建：aapt2 -> javac -> d8 -> zip -> zipalign -> apksigner"""
import argparse
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

PROJECT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = os.path.join(PROJECT, "app", "src", "main")
OUT = os.path.join(PROJECT, "out")
KEYSTORE = os.path.join(PROJECT, "tools", "keystore", "ocrshare.jks")
KS_PASS = "ocrshare"
KS_ALIAS = "ocrshare"
PKG = "cn.yg.ocrshare"
VERSION_NAME = "1.1.9"
VERSION_CODE = "11"
MIN_SDK = "26"
TARGET_SDK = "36"


def sdk_dir():
    for key in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        v = os.environ.get(key)
        if v and os.path.isdir(v):
            return v
    for guess in (r"D:\Software\Android_SDK", os.path.expanduser("~/Android/Sdk")):
        if os.path.isdir(guess):
            return guess
    raise SystemExit("找不到 Android SDK")


def newest(path):
    items = [os.path.join(path, n) for n in os.listdir(path)]
    items = [i for i in items if os.path.isdir(i)]
    return sorted(items)[-1] if items else None


def find_tool(name):
    found = shutil.which(name)
    if found:
        return found
    jh = os.environ.get("JAVA_HOME")
    if jh:
        cand = os.path.join(jh, "bin", name)
        if os.path.exists(cand) or os.path.exists(cand + ".exe"):
            return cand
    raise SystemExit("找不到工具: " + name)


def decode(b):
    for enc in ("utf-8", "gbk", "latin-1"):
        try:
            return b.decode(enc)
        except Exception:
            continue
    return b.decode("utf-8", "replace")


def run(cmd, cwd=None, quiet=False):
    printable = " ".join(cmd) if isinstance(cmd, list) else str(cmd)
    print(">>", printable)
    r = subprocess.run(cmd, cwd=cwd, shell=isinstance(cmd, str),
                       stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    text = decode(r.stdout)
    if text.strip() and not quiet:
        print(text.strip()[:4000])
    if r.returncode != 0:
        print(text)
        raise SystemExit("命令失败 (%d): %s" % (r.returncode, printable))
    return text


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--clean", action="store_true")
    args = ap.parse_args()

    sdk = sdk_dir()
    bt = newest(os.path.join(sdk, "build-tools"))
    platform = newest(os.path.join(sdk, "platforms"))
    android_jar = os.path.join(platform, "android.jar")
    aapt2 = os.path.join(bt, "aapt2.exe")
    if not os.path.exists(aapt2):
        aapt2 = os.path.join(bt, "aapt2")
    zipalign = os.path.join(bt, "zipalign.exe")
    if not os.path.exists(zipalign):
        zipalign = os.path.join(bt, "zipalign")
    d8_jar = os.path.join(bt, "lib", "d8.jar")
    apksigner_jar = os.path.join(bt, "lib", "apksigner.jar")
    java = find_tool("java")
    javac = find_tool("javac")
    jar = find_tool("jar")
    keytool = find_tool("keytool")

    print("SDK:", sdk, "| build-tools:", bt, "| platform:", platform)

    stage = os.path.join(tempfile.gettempdir(), "ocrshare_build")
    if args.clean and os.path.isdir(stage):
        shutil.rmtree(stage)
    os.makedirs(stage, exist_ok=True)
    for sub in ("src", "res", "res_flat", "gen", "classes", "dex"):
        d = os.path.join(stage, sub)
        if os.path.isdir(d):
            shutil.rmtree(d)
        os.makedirs(d)

    print("\n=== 1/6 拷贝源码 ===")
    shutil.copytree(os.path.join(APP, "java"), os.path.join(stage, "src", "java"), dirs_exist_ok=True)
    shutil.copytree(os.path.join(APP, "res"), os.path.join(stage, "res"), dirs_exist_ok=True)
    manifest = open(os.path.join(APP, "AndroidManifest.xml"), encoding="utf-8").read()
    if "package=" not in manifest.split(">")[0]:
        manifest = manifest.replace("<manifest ", '<manifest package="%s" ' % PKG, 1)
    with open(os.path.join(stage, "AndroidManifest.xml"), "w", encoding="utf-8") as fh:
        fh.write(manifest)

    print("\n=== 2/6 资源 (aapt2) ===")
    run([aapt2, "compile", "--dir", os.path.join(stage, "res"), "-o", os.path.join(stage, "res_flat")])
    flats = []
    for dp, _, fn in os.walk(os.path.join(stage, "res_flat")):
        for f in fn:
            if f.endswith(".flat"):
                flats.append(os.path.join(dp, f))
    link = [aapt2, "link", "-o", os.path.join(stage, "base.apk"), "-I", android_jar,
            "--manifest", os.path.join(stage, "AndroidManifest.xml"),
            "--java", os.path.join(stage, "gen"),
            "--min-sdk-version", MIN_SDK, "--target-sdk-version", TARGET_SDK,
            "--version-code", VERSION_CODE, "--version-name", VERSION_NAME,
            "--auto-add-overlay"]
    for f in flats:
        link += ["-R", f]
    run(link)

    print("\n=== 3/6 Java ===")
    sources = []
    for root in (os.path.join(stage, "src"), os.path.join(stage, "gen")):
        for dp, _, fn in os.walk(root):
            for f in fn:
                if f.endswith(".java"):
                    sources.append(os.path.join(dp, f))
    print("java 文件:", len(sources))
    argfile = os.path.join(stage, "sources.txt")
    with open(argfile, "w", encoding="utf-8") as fh:
        for s in sources:
            fh.write('"%s"\n' % s.replace("\\", "/"))
    run([javac, "-encoding", "UTF-8", "--release", "11", "-nowarn",
         "-classpath", android_jar, "-d", os.path.join(stage, "classes"), "@" + argfile])

    print("\n=== 4/6 dex (d8) ===")
    classes_jar = os.path.join(stage, "classes.jar")
    run([jar, "cf", classes_jar, "-C", os.path.join(stage, "classes"), "."])
    run([java, "-cp", d8_jar, "com.android.tools.r8.D8", "--release",
         "--min-api", MIN_SDK, "--lib", android_jar,
         "--output", os.path.join(stage, "dex"), classes_jar])

    print("\n=== 5/6 打包 ===")
    unsigned = os.path.join(stage, "unsigned.apk")
    shutil.copy2(os.path.join(stage, "base.apk"), unsigned)
    dex = os.path.join(stage, "dex", "classes.dex")
    if not os.path.exists(dex):
        raise SystemExit("没有 classes.dex")
    with zipfile.ZipFile(unsigned, "a", zipfile.ZIP_DEFLATED) as z:
        z.write(dex, "classes.dex")

    if not os.path.exists(KEYSTORE):
        os.makedirs(os.path.dirname(KEYSTORE), exist_ok=True)
        run([keytool, "-genkeypair", "-keystore", KEYSTORE, "-alias", KS_ALIAS,
             "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
             "-storepass", KS_PASS, "-keypass", KS_PASS,
             "-dname", "CN=YG, OU=Local, O=OcrShare, L=Local, ST=Local, C=CN"])

    aligned = os.path.join(stage, "aligned.apk")
    run([zipalign, "-p", "-f", "4", unsigned, aligned])

    os.makedirs(OUT, exist_ok=True)
    final = os.path.join(OUT, "ScreenshotOCR-%s.apk" % VERSION_NAME)
    run([java, "-jar", apksigner_jar, "sign",
         "--ks", KEYSTORE, "--ks-pass", "pass:" + KS_PASS, "--key-pass", "pass:" + KS_PASS,
         "--ks-key-alias", KS_ALIAS, "--v1-signing-enabled", "true",
         "--v2-signing-enabled", "true", "--v3-signing-enabled", "true",
         "--out", final, aligned])

    print("\n=== 6/6 校验 ===")
    run([java, "-jar", apksigner_jar, "verify", "--print-certs", final])
    print("\nAPK :", final)
    print("大小:", round(os.path.getsize(final) / 1024.0, 1), "KB")


if __name__ == "__main__":
    main()
