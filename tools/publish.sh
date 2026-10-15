#!/usr/bin/env bash
# ---------------------------------------------------------------------------
#  انتشار نسخهٔ جدید NZVPN:  ساخت APK → کپی در apk/ → ارسال به تلگرام
#
#  استفاده:
#     bash tools/publish.sh                # ساخت + ارسال
#     bash tools/publish.sh --no-build     # فقط ارسال آخرین APK ساخته‌شده
#     bash tools/publish.sh --note "متن"   # یادداشت تغییرات این نسخه
#
#  پیش‌نیاز: telegram.config با TG_TOKEN و TG_CHAT_ID (یک‌بار: bash tools/telegram.sh wait)
# ---------------------------------------------------------------------------
set -euo pipefail
cd "$(dirname "$0")/.."

BUILD=1
TYPE=release        # release (پیش‌فرض) یا debug
NOTE=""
while [ $# -gt 0 ]; do
  case "$1" in
    --no-build) BUILD=0 ;;
    --debug) TYPE=debug ;;
    --release) TYPE=release ;;
    --note) NOTE="${2:?متن یادداشت را بدهید}"; shift ;;
    *) echo "گزینهٔ ناشناخته: $1"; exit 1 ;;
  esac
  shift
done

# ---- نسخه از build.gradle.kts ----
VERSION="$(grep -m1 'versionName' app/build.gradle.kts | sed 's/.*"\(.*\)".*/\1/')"
CODE="$(grep -m1 'versionCode' app/build.gradle.kts | sed 's/[^0-9]//g')"
echo "نسخه: $VERSION (versionCode $CODE)"

# ---- ساخت ----
if [ "$BUILD" = 1 ]; then
  if [ "$TYPE" = release ]; then
    [ -f telegram.config ] && . ./telegram.config 2>/dev/null || true
    : "${nzKeystore:=keystore/nz-release.jks}"
    : "${nzKeystorePassword:?رمز کلید امضا تنظیم نشده (keystore/keystore.properties)}"
    : "${nzKeyAlias:=nzvpn}"
    : "${nzKeyPassword:=$nzKeystorePassword}"
    export nzKeystore nzKeystorePassword nzKeyAlias nzKeyPassword
    bash tools/build-release.sh
  else
    bash tools/build.sh
  fi
fi

if [ "$TYPE" = release ]; then
  SRC="app/build/outputs/apk/release/app-universal-release.apk"
  TAG="universal-release"
  TARGET="arm64-v8a + armeabi-v7a"
else
  SRC="app/build/outputs/apk/debug/app-arm64-v8a-debug.apk"
  TAG="arm64-v8a-debug"
  TARGET="arm64-v8a"
fi
[ -f "$SRC" ] || { echo "APK پیدا نشد: $SRC" >&2; exit 1; }
DEST="apk/NZVPN-${VERSION}-${TAG}.apk"
cp "$SRC" "$DEST"

SIZE_B="$(stat -c%s "$DEST")"
SIZE_H="$(du -h "$DEST" | cut -f1)"
SHA="$(sha256sum "$DEST" | cut -d' ' -f1)"
echo "APK: $DEST  ($SIZE_H)"

# ---- ارسال به تلگرام ----
CAPTION="📦 NZVPN ${VERSION}  (versionCode ${CODE})
• ${TAG} · ${TARGET} · ${SIZE_H}
• نصب: فایل را باز کنید و اجازهٔ «نصب از منابع نامعلوم» بدهید
• پس از نصب: از دکمهٔ + سرور یا ساب اضافه کنید؛ مخزن با دکمهٔ تازه‌سازی خوانده می‌شود
🔒 sha256: ${SHA}"
[ -n "$NOTE" ] && CAPTION="${CAPTION}

🆕 تغییرات:
${NOTE}"

bash tools/telegram.sh send "$DEST" "$CAPTION"
