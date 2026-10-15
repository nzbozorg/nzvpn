# ---------------------------------------------------------------------------
#  قوانین ProGuard / R8 برای NZVPN
# ---------------------------------------------------------------------------

# کلاس‌های تولیدشده توسط gomobile (هستهٔ Xray) باید دست‌نخورده بمانند
-keep class libv2ray.** { *; }
-keep class go.** { *; }
-keepclassmembers class * extends go.Seq$Proxy { *; }
-dontwarn libv2ray.**
-dontwarn go.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Compose
-dontwarn androidx.compose.**

# ---------------------------------------------------------------------------
#  سبک‌کردن R8 برای ساخت روی ماشین‌های کم‌حافظه (۲GB).
#  با این خط، R8 فقط حذف کدهای بی‌استفاده + تغییر نام می‌دهد و از
#  بهینه‌سازی‌های سنگین (inline/class merging) صرف‌نظر می‌کند.
#  اگر روی ماشین قوی بیلد می‌کنید، این خط را حذف کنید (APK کمی سریع‌تر می‌شود).
-dontoptimize
