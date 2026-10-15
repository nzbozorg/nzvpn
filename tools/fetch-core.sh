#!/usr/bin/env bash
# ---------------------------------------------------------------------------
#  دریافت هستهٔ Xray (libv2ray.aar) برای NZVPN
#  اپ بدون این فایل بیلد نمی‌شود (build.gradle.kts با پیام فارسی متوقف می‌شود).
#  اجرا:  bash tools/fetch-core.sh
# ---------------------------------------------------------------------------
set -euo pipefail
VERSION="${XRAY_LIB_VERSION:-v26.10.10}"
SHA256_EXPECTED="15db760bfbc24c0492ee997784b48a7b5e1bc5032950d617cd252fc2195f7ac7"
DEST="$(cd "$(dirname "$0")/.." && pwd)/app/libs/libv2ray.aar"
URL="https://github.com/2dust/AndroidLibXrayLite/releases/download/${VERSION}/libv2ray.aar"

mkdir -p "$(dirname "$DEST")"
echo "→ دانلود ${URL}"
curl -fL --retry 3 -o "$DEST.part" "$URL"
mv "$DEST.part" "$DEST"

ACTUAL="$(sha256sum "$DEST" | awk '{print $1}')"
if [ "$ACTUAL" != "$SHA256_EXPECTED" ]; then
  echo "هش فایل مطابق نسخهٔ آزمایش‌شده نیست:"
  echo "  انتظار: $SHA256_EXPECTED"
  echo "  دریافت: $ACTUAL"
  echo "اگر نسخهٔ دیگری از هسته را می‌خواهید، همین مقدار را در اسکریپت به‌روز کنید."
  exit 1
fi
echo "✓ هسته ذخیره شد: $DEST ($(du -h "$DEST" | cut -f1))"
