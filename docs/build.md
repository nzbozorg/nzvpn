# ساخت، نصب و انتشار NZVPN

## پیش‌نیازها
- JDK 17 (Temurin/OpenJDK)
- Android SDK: `platforms;android-36` و `build-tools;36.0.0`
- Gradle لازم نیست نصب شود (wrapper موجود است: Gradle 9.1.0)

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH
```

## ۱) هستهٔ Xray
```bash
bash tools/fetch-core.sh          # app/libs/libv2ray.aar  (~62MB)
```
اگر فایل نباشد، Gradle با پیام فارسی متوقف می‌شود. نسخه را می‌توانید عوض کنید:
```bash
XRAY_LIB_VERSION=v26.10.10 bash tools/fetch-core.sh
```
(هش نسخهٔ آزمایش‌شده داخل اسکریپت قفل شده است: `15db760b…5f7ac7`)

## ۲) بیلد
```bash
bash tools/build.sh                       # دیباگ + تقسیم بر معماری
./gradlew :app:assembleDebug              # همان کار، دستی
./gradlew :app:compileDebugKotlin         # فقط کامپایل سریع (تست)
./gradlew clean                           # پاک‌سازی
```
خروجی‌ها در `app/build/outputs/apk/debug/`.

## ۳) نصب روی گوشی
```bash
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```
یا فایل APK را به گوشی بفرستید و از فایل‌منیجر نصب کنید
(اجازهٔ «نصب از منابع نامعلوم» لازم است).

## ۴) نسخهٔ انتشار (release) و امضا

کلید امضا **نزد مالک برنامه ساخته و نگهداری شده است**؛ داخل ZIP سورس نیست:

| مورد | مقدار |
|---|---|
| فایل کلید | `keystore/nz-release.jks` |
| نام کلید | `nzvpn` |
| رمز (کلید و مخزن) | نزد مالک برنامه؛ داخل ZIP سورس قرار نمی‌گیرد |
| صاحب گواهی | CN=NZBOZORG, O=NZBOZORG, OU=NZVPN, C=IR |
| اعتبار | ۱۰۰۰۰ روز (تا ۲۰۵۴) |

مقادیر در `keystore/keystore.properties` هستند (این پوشه در `.gitignore` است).

```bash
# رمزها را بیرون از پروژه تنظیم کنید؛ keystore قبلی لازم است.
export nzKeystorePassword='<رمز-شخصی>'
export nzKeyPassword="$nzKeystorePassword"
# ساخت APK انتشار با همان کلید
./gradlew :app:assembleRelease \
  -PnzAbis=arm64-v8a,armeabi-v7a -PnzUniversalApk=true \
  -PnzKeystore=keystore/nz-release.jks \
  -PnzKeystorePassword="$nzKeystorePassword" \
  -PnzKeyAlias=nzvpn -PnzKeyPassword="$nzKeyPassword"

# یا با اسکریپت انتشار (متغیرهای بالا و telegram.config محلی لازم‌اند)
bash tools/publish.sh --note "توضیح این نسخه"
```

> ⚠️ **R8 (کوچک‌سازی کد) به‌صورت پیش‌فرض خاموش است** چون روی ماشین‌های کم‌حافظه
> (کمتر از ~۱٫۵GB رمز آزاد) روند بیلد با کمبود حافظه کشته می‌شود.
> روی ماشین قوی اجرا کنید:
> `./gradlew :app:assembleRelease -PnzMinify=true …`

## ۵) فعال‌کردن آپدیت داخل برنامه از گیت‌هاب

اپ فقط **GitHub Releases پایدار** در مخزن عمومی
`https://github.com/nzbozorg/nzvpn` را می‌خواند؛ قرار دادن APK در پوشهٔ گیت
یا ارسال آن به تلگرام به‌تنهایی قابلیت آپدیت را فعال نمی‌کند.

1. مخزن را عمومی کنید و سورس (بدون `keystore/`، `telegram.config`، AAR و APK) را منتشر کنید.
2. فایل **امضاشده با همان کلید نصب‌شده** را بسازید:
   `apk/NZVPN-1.0.8-universal-release.apk`.
3. در GitHub → **Releases** → **Draft a new release** برچسب `v1.0.8` بگذارید؛
   گزینهٔ draft/prerelease را خاموش بگذارید و APK را با همین **نام دقیق** پیوست کنید.
4. برای نسخه‌های بعدی هم برچسب `v1.0.9` و فایل
   `NZVPN-1.0.9-universal-release.apk` (versionCode بالاتر و همان گواهی) بگذارید.
5. برنامه دکمهٔ «بررسی نسخهٔ جدید» را دارد؛ پس از دریافت و کنترل امضا/نسخه/ABI،
   نصب‌کنندهٔ خود اندروید با تأیید کاربر اجرا می‌شود. روی اندروید ۸ به بالا ممکن است
   لازم باشد اجازهٔ «نصب از این منبع» را **اختیاری و با انتخاب کاربر** فعال کنید.

تا وقتی انتشار رسمی ساخته نشود، API گیت‌هاب 404 می‌دهد و برنامه پیام فارسی
«هنوز نسخه‌ای در انتشارهای گیت‌هاب نیست» نشان می‌دهد. حالت آفلاین یا انتشار با
کلید دیگر نیز هرگز به‌طور خودکار نصب نمی‌شود. این روش ویژهٔ توزیع مستقیم از
گیت‌هاب است؛ برای نسخهٔ پلی‌استور سیاست `REQUEST_INSTALL_PACKAGES` را بازبینی کنید.

## ۶) انتشار در گوگل‌پلی
```bash
export nzKeystore=nz-release.jks nzKeystorePassword=… nzKeyAlias=nzvpn nzKeyPassword=…
bash tools/build-release.sh               # APK انتشار مستقیم
./gradlew :app:bundleRelease -PnzKeystore=… -PnzKeystorePassword=… -PnzKeyAlias=… -PnzKeyPassword=…  # AAB پلی
```
نکته‌های پلی:
- سیاست VPN پلی: در فرم Play Console توضیح دهید VPN برای چه کاری است
  (این اپ «کلاینت شخصی» است: کاربر سرورهای خودش را وارد می‌کند).
- فایل `app/proguard-rules.pro` برای R8 آماده است (نگه‌داشتن کلاس‌های libv2ray).
- کوچک‌سازی منابع همراه R8 فقط با `-PnzMinify=true` فعال می‌شود؛ پیش‌فرض در ماشین کم‌حافظه خاموش است.

## ۷) رفع اشکال
| نشانه | راه‌حل |
|---|---|
| `SDK location not found` | `ANDROID_HOME` را تنظیم کنید یا `local.properties` بسازید |
| کشته‌شدن Gradle روی ماشین کم‌حافظه | `gradle.properties` (تنظیمات سبک از قبل هست) |
| «هستهٔ Xray پیدا نشد» هنگام build | `bash tools/fetch-core.sh` |
| بیلد با کمبود حافظه کشته می‌شود (داِمن ناپدید می‌شود) | `-PnzMinify=true` را نزنید؛ فایل‌های موقت `/tmp` را پاک کنید؛ `--max-workers=1` |
| اپ سرور نشان نمی‌دهد | فایل `configs.txt` در مخزن گیت‌هاب نیست (docs/github-file-format.md) |
| نصب روی گوشی شکست می‌خورد | نسخهٔ انتشار مشترک `arm64-v8a` + `armeabi-v7a` را نصب کنید؛ امضای به‌روزرسانی باید با نسخهٔ قبلی یکی باشد |
