#!/usr/bin/env bash
# ---------------------------------------------------------------------------
#  ساخت APK نسخهٔ دیباگ NZVPN  (خروجی: app/build/outputs/apk/debug/)
#    app-arm64-v8a-debug.apk    ← برای تقریباً همهٔ گوشی‌های امروزی
#    app-armeabi-v7a-debug.apk  ← گوشی‌های قدیمی‌تر
#    app-x86_64-debug.apk       ← شبیه‌ساز/تبلت‌های اینتل
#    app-universal-debug.apk    ← همه با هم (حجیم‌تر)
# ---------------------------------------------------------------------------
set -euo pipefail
cd "$(dirname "$0")/.."
[ -f app/libs/libv2ray.aar ] || { echo "هسته موجود نیست؛ اول: bash tools/fetch-core.sh"; exit 1; }
./gradlew :app:assembleDebug --console=plain "$@"
echo "خروجی:"; ls -la app/build/outputs/apk/debug/*.apk
