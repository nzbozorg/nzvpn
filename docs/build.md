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
bash ./gradlew :app:assembleDebug              # همان کار، دستی
bash ./gradlew :app:compileDebugKotlin         # فقط کامپایل سریع (تست)
bash ./gradlew clean                           # پاک‌سازی
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
# کلید قبلی و رمزها را فقط در محیط محلی خود تنظیم کنید، نه در مخزن عمومی.
export nzKeystore=keystore/nz-release.jks nzKeyAlias=nzvpn
export nzKeystorePassword='<رمز-شخصی>'
export nzKeyPassword="$nzKeystorePassword"
# اسکریپت، رمزها را در آرگومان خط فرمان آشکار نمی‌کند.
bash tools/build-release.sh
```

> ⚠️ **R8 (کوچک‌سازی کد) به‌صورت پیش‌فرض خاموش است** چون روی ماشین‌های کم‌حافظه
> (کمتر از ~۱٫۵GB رمز آزاد) روند بیلد با کمبود حافظه کشته می‌شود.
> روی ماشین قوی اجرا کنید:
> `bash ./gradlew :app:assembleRelease -PnzMinify=true …`

## ۵) فعال‌کردن آپدیت داخل برنامه از گیت‌هاب

اپ فقط **GitHub Releases پایدار** در مخزن عمومی
`https://github.com/nzbozorg/nzvpn` را می‌خواند؛ قرار دادن APK در پوشهٔ گیت
یا ارسال آن به تلگرام به‌تنهایی قابلیت آپدیت را فعال نمی‌کند.

1. **اول سورس ۱.۰.۹ را روی `main` کامیت و push کنید.** مخزن عمومی در پایان
   نسخهٔ ۱.۰.۸ را داشت؛ تگ جدید اگر از آن کامیت ساخته شود، آرشیو خودکار GitHub
   سورس اشتباه خواهد داشت. کلید، توکن، AAR و APK را کامیت نکنید.
2. APK مشترکِ امضاشده با همان کلید را بسازید/بردارید:
   `apk/NZVPN-1.0.9-universal-release.apk`، با `versionCode 10`.
3. در GitHub → **Releases** → **Draft a new release** برچسب جدید `v1.0.9`
   را **از کامیت سورس ۱.۰.۹** بسازید؛ برای ۱.۰.۸ چیزی جایگزین نکنید.
4. فایل‌های `NZVPN-1.0.9-universal-release.apk` و
   `NZVPN-1.0.9-source.zip` را جداگانه به Assets همان Release اضافه کنید؛
   متن [`release-notes-v1.0.9.md`](./release-notes-v1.0.9.md) را در توضیحات
   بگذارید. Draft و Pre-release را برای انتشار پایدار خاموش کنید.
5. آپدیت درون‌برنامه‌ای تنها برای نسخه‌های **جدیدتر از نسخهٔ نصب‌شده** نمایش داده
   می‌شود؛ فایل APK پیش از بازشدن نصب‌کننده از نظر نام، ABI، نسخه و امضا بررسی
   می‌شود و نصب نهایی همیشه با انتخاب خود کاربر است.

نسخهٔ عمومی ۱.۰.۸ از قبل وجود دارد. تا پیش از انتشار ۱.۰.۹، نصب ۱.۰.۹
طبیعتاً آپدیت جدیدتری از GitHub پیدا نمی‌کند. حالت آفلاین یا انتشار با کلید
دیگر نیز هرگز به‌طور خودکار نصب نمی‌شود. این روش ویژهٔ توزیع مستقیم از
گیت‌هاب است؛ برای نسخهٔ پلی‌استور سیاست `REQUEST_INSTALL_PACKAGES` را بازبینی کنید.

## ۶) انتشار در گوگل‌پلی
```bash
export nzKeystore=keystore/nz-release.jks nzKeystorePassword=… nzKeyAlias=nzvpn nzKeyPassword=…
bash tools/build-release.sh               # APK انتشار مستقیم
# برای AAB همان اطلاعات را با متغیرهای ORG_GRADLE_PROJECT_nz... به Gradle بدهید؛
# کلیدها و رمزها را در آرگومان خط فرمان یا مخزن عمومی قرار ندهید.
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
| اپ سرور نشان نمی‌دهد | دسترسی شبکه و محتوای `configs.txt` و ساب‌های داخلش را بررسی کنید (docs/github-file-format.md) |
| نصب روی گوشی شکست می‌خورد | نسخهٔ انتشار مشترک `arm64-v8a` + `armeabi-v7a` را نصب کنید؛ امضای به‌روزرسانی باید با نسخهٔ قبلی یکی باشد |
