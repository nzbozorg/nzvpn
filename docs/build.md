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

> نسخهٔ ۱.۱.۰ فعلاً **فقط برای آزمون خصوصی** است. مراحل انتشار زیر دستورِ
> اجرای فوری نیستند؛ فقط **پس از تأیید صریح مالک** انجام شوند.

اپ فقط **GitHub Releases پایدار** در مخزن عمومی
`https://github.com/nzbozorg/nzvpn` را می‌خواند؛ قرار دادن APK در پوشهٔ گیت
یا ارسال آن به تلگرام به‌تنهایی قابلیت آپدیت را فعال نمی‌کند.

1. اگر سورس ۱.۰.۹ هنوز در مخزن عمومی نیست، **ابتدا همان سورس ۱.۰.۹ را روی
   `main` کامیت و push کنید** و فقط در صورت تأیید مالک، تگ/Release متناظر آن را
   از همان کامیت بسازید. مخزن عمومی قبلاً روی سورس ۱.۰.۸ بود. تگ ۱.۰.۹ روی
   کامیت ۱.۰.۸، آرشیو خودکار سورس اشتباه می‌سازد.
2. پس از تأیید جداگانه برای **۱.۱.۰**، سورس ۱.۱.۰ را commit/push کنید؛
   کلید، توکن، AAR، APK و فایل‌های شخصی را کامیت نکنید.
3. APK مشترکِ ۱.۱.۰ را با همان کلید پیشین بردارید:
   `apk/NZVPN-1.1.0-universal-release.apk`، با `versionCode 11`.
4. در GitHub → **Releases** → **Draft a new release** برچسب `v1.1.0` را
   **از کامیت سورس ۱.۱.۰** بسازید، نه از کامیت نسخهٔ قبلی؛ فایل‌های
   `NZVPN-1.1.0-universal-release.apk` و `NZVPN-1.1.0-source.zip` را جداگانه
   به Assets اضافه کنید و [`release-notes-v1.1.0.md`](./release-notes-v1.1.0.md)
   را در توضیحات بگذارید. Draft و Pre-release را برای انتشار پایدار خاموش کنید.
5. آپدیت درون‌برنامه‌ای فقط برای نسخه‌های **جدیدتر از نسخهٔ نصب‌شده** پیدا می‌شود؛
   متن پاپ‌آپِ پس از ارتقا از Release **همان نسخهٔ نصب‌شده** می‌آید. فایل APK
   پیش از بازشدن نصب‌کننده از نظر نام، ABI، نسخه و امضا بررسی می‌شود و نصب
   نهایی همیشه با انتخاب خود کاربر است.

تا وقتی Release ۱.۱.۰ با برچسب متناظر ساخته نشده، نصب خصوصی ۱.۱.۰ یادداشت
آن نسخه را از GitHub نشان نمی‌دهد؛ با انتشار واقعی در ورود بعدی دوباره تلاش
خواهد کرد. حالت آفلاین یا انتشار با کلید دیگر نیز هرگز به‌طور خودکار نصب
نمی‌شود. این روش ویژهٔ توزیع مستقیم از گیت‌هاب است؛ برای نسخهٔ پلی‌استور
سیاست `REQUEST_INSTALL_PACKAGES` را بازبینی کنید.

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
