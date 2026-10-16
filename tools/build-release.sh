#!/usr/bin/env bash
# ---------------------------------------------------------------------------
#  ساخت APK نسخهٔ انتشار NZVPN (امضا؛ R8 پیش‌فرض خاموش است)
#
#  برای به‌روزرسانی نصب قبلی، حتماً همان فایل کلید امضا و alias قبلی را
#  استفاده کنید. کلید تازه فقط برای اولین انتشار ممکن است ساخته شود.
#
#  سپس:
#     export nzKeystore=nz-release.jks
#     export nzKeystorePassword=...
#     export nzKeyAlias=nzvpn
#     export nzKeyPassword=...
#     bash tools/build-release.sh
# ---------------------------------------------------------------------------
set -euo pipefail
cd "$(dirname "$0")/.."
: "${nzKeystore:?متغیر nzKeystore (مسیر فایل jks) تنظیم نشده}"
: "${nzKeystorePassword:?متغیر nzKeystorePassword تنظیم نشده}"
: "${nzKeyAlias:?متغیر nzKeyAlias تنظیم نشده}"
: "${nzKeyPassword:?متغیر nzKeyPassword تنظیم نشده}"

# رمزها در آرگومان خط فرمان/لاگ پردازه ظاهر نشوند؛ Gradle آنها را از محیط می‌خواند.
export ORG_GRADLE_PROJECT_nzKeystore="$nzKeystore"
export ORG_GRADLE_PROJECT_nzKeystorePassword="$nzKeystorePassword"
export ORG_GRADLE_PROJECT_nzKeyAlias="$nzKeyAlias"
export ORG_GRADLE_PROJECT_nzKeyPassword="$nzKeyPassword"

# یک APK مشترک برای گوشی‌های ۶۴بیتی و ۳۲بیتی (بدون ABIهای شبیه‌ساز).
bash ./gradlew :app:assembleRelease --console=plain --no-daemon --max-workers=1 \
  -PnzAbis=arm64-v8a,armeabi-v7a -PnzUniversalApk=true

APK="app/build/outputs/apk/release/app-universal-release.apk"
# split.include به‌تنهایی universal را محدود نمی‌کند؛ محتوا را هم بررسی کن.
python3 - "$APK" <<'PY'
import sys
import zipfile
with zipfile.ZipFile(sys.argv[1]) as archive:
    abis = {name.split('/')[1] for name in archive.namelist()
            if name.startswith('lib/') and name.endswith('/libgojni.so')}
expected = {'arm64-v8a', 'armeabi-v7a'}
if abis != expected:
    raise SystemExit(f'ABIهای APK مشترک درست نیستند: {sorted(abis)} (انتظار: {sorted(expected)})')
print('ABIهای هسته در APK مشترک:', ', '.join(sorted(abis)))
PY
echo "APK مشترک arm64 و arm32:"; ls -lh "$APK"
