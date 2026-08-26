#!/usr/bin/env bash
set -e

# 本脚本使用 Android SDK 自带命令行工具离线构建 APK，
# 不依赖 Gradle / Maven / Android Studio。
# 运行环境：Windows + Git Bash + JDK 17+ + Android SDK

ROOT="$(cd "$(dirname "$0")" && pwd -W)"
SDK="${ANDROID_SDK:-D:/SOFTWARE/android_sdk}"
BT="${SDK}/build-tools/35.0.0"
AJ="${SDK}/platforms/android-34/android.jar"
OUT="${ROOT}/build"
OBJ="${OUT}/obj"

echo "ROOT=${ROOT}"
echo "SDK=${SDK}"

echo "==> 1. compile java"
rm -rf "$OBJ"; mkdir -p "$OBJ"
javac -encoding UTF-8 -source 17 -target 17 -cp "$AJ" -d "$OBJ" \
  "$ROOT/src/com/cuixinyuan/pinyin/MainActivity.java" \
  "$ROOT/src/com/cuixinyuan/pinyin/SnakeView.java"
echo "    java classes: $(ls "$OBJ/com/cuixinyuan/pinyin" | wc -l) files"

echo "==> 2. d8 dex"
DEXDIR="$OUT/dex"; rm -rf "$DEXDIR"; mkdir -p "$DEXDIR"
java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --min-api 21 --lib "$AJ" --output "$DEXDIR" "$OBJ/com/cuixinyuan/pinyin/"*.class
echo "    dex: $(ls -l "$DEXDIR/classes.dex" | awk '{print $5}') bytes"

echo "==> 3. aapt2 compile resources"
"$BT/aapt2.exe" compile -o "$OUT/res.flata" --dir "$ROOT/res"
echo "    res compiled"

echo "==> 4. aapt2 link (manifest + res + assets)"
"$BT/aapt2.exe" link -I "$AJ" \
  -o "$OUT/app-unsigned.apk" \
  --manifest "$ROOT/AndroidManifest.xml" \
  -A "$ROOT/assets" \
  "$OUT/res.flata"
echo "    base apk built"

echo "==> 5. add classes.dex into apk"
jar uf "$OUT/app-unsigned.apk" -C "$DEXDIR" classes.dex
echo "    dex added"

echo "==> 6. generate debug keystore"
if [ ! -f "$OUT/debug.keystore" ]; then
  keytool -genkeypair -v -keystore "$OUT/debug.keystore" -alias androiddebugkey \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass android -keypass android \
    -dname "CN=Android Debug,O=Android,C=US"
fi

echo "==> 7. zipalign"
rm -f "$OUT/app-aligned.apk"
"$BT/zipalign.exe" -p 4 "$OUT/app-unsigned.apk" "$OUT/app-aligned.apk"

echo "==> 8. apksigner"
rm -f "$ROOT/pinyin-app.apk"
java -jar "$BT/lib/apksigner.jar" sign \
  --ks "$OUT/debug.keystore" --ks-key-alias androiddebugkey \
  --ks-pass pass:android --key-pass pass:android \
  --out "$ROOT/pinyin-app.apk" "$OUT/app-aligned.apk"
echo "    signed apk: $ROOT/pinyin-app.apk"

echo "==> verify"
java -jar "$BT/lib/apksigner.jar" verify --verbose "$ROOT/pinyin-app.apk" | head -20
ls -l "$ROOT/pinyin-app.apk"
