#!/usr/bin/env bash
# Construit l'APK sans Gradle ni SDK Google : aapt2 + javac + dx + apksigner.
#
# La cle WaveSpeed est injectee a la compilation, jamais stockee dans le depot :
#   - soit via la variable d'environnement WAVESPEED_KEY
#   - soit via un fichier local.key (ignore par git)
set -euo pipefail

cd "$(dirname "$0")"

BT=${BT:-/usr/lib/android-sdk/build-tools/debian}
AJAR=${AJAR:-/opt/android-all/android-all-14.jar}
MIN_SDK=29
TARGET_SDK=34
PKG=com.lvx.rpkeyboard
OUT=build
APK=$OUT/clavier-rp.apk

KEY=${WAVESPEED_KEY:-}
if [ -z "$KEY" ] && [ -f local.key ]; then
  KEY=$(tr -d '\r\n' < local.key)
fi
if [ -z "$KEY" ]; then
  echo "ERREUR : cle absente. Exporte WAVESPEED_KEY ou cree local.key" >&2
  exit 1
fi

rm -rf "$OUT"
mkdir -p "$OUT"/{classes,gen,src,flat}

echo "[1/7] sources + injection de la cle"
cp -r src/* "$OUT/src/"
grep -rl '@WAVESPEED_KEY@' "$OUT/src" | while read -r f; do
  sed -i "s|@WAVESPEED_KEY@|$KEY|g" "$f"
done

echo "[2/7] compilation des ressources"
"$BT/aapt2" compile --dir res -o "$OUT/res.zip"

echo "[3/7] lien des ressources"
"$BT/aapt2" link \
  -o "$OUT/base.apk" \
  -I "$AJAR" \
  --manifest AndroidManifest.xml \
  --java "$OUT/gen" \
  --min-sdk-version "$MIN_SDK" \
  --target-sdk-version "$TARGET_SDK" \
  "$OUT/res.zip"

echo "[4/7] compilation java"
find "$OUT/src" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
# --release 8 : bytecode accepte par dx. Le framework vient du classpath,
# car android-all ne fournit pas java.lang.
javac --release 8 -nowarn -Xlint:-options \
  -encoding UTF-8 \
  -classpath "$AJAR" \
  -d "$OUT/classes" \
  @"$OUT/sources.txt"

echo "[5/7] dex"
dalvik-exchange --dex --min-sdk-version "$MIN_SDK" \
  --output="$OUT/classes.dex" "$OUT/classes"

echo "[6/7] assemblage"
cp "$OUT/base.apk" "$OUT/unaligned.apk"
( cd "$OUT" && zip -q -j unaligned.apk classes.dex )
zipalign -f 4 "$OUT/unaligned.apk" "$OUT/aligned.apk"

echo "[7/7] signature"
if [ ! -f "$OUT/debug.jks" ]; then
  keytool -genkeypair -v \
    -keystore "$OUT/debug.jks" \
    -storepass android -keypass android \
    -alias clavierrp \
    -keyalg RSA -keysize 2048 -validity 10950 \
    -dname "CN=Clavier RP, OU=perso, O=perso, C=FR" >/dev/null 2>&1
fi
apksigner sign \
  --ks "$OUT/debug.jks" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --ks-key-alias clavierrp \
  --min-sdk-version "$MIN_SDK" \
  --out "$APK" \
  "$OUT/aligned.apk"

apksigner verify --print-certs "$APK" | head -3
echo
echo "APK pret : $APK"
ls -lh "$APK"
