# قالب فایل کانفیگ گیت‌هاب (configs.txt)

این سند دقیقاً همان کاری است که `net/RepoClient.kt` + `parser/ShareLinkParser.kt` و
`parser/SubscriptionParser.kt` در اپ انجام می‌دهند.

## ۱) آدرس فایل

اپ به‌طور پیش‌فرض `https://github.com/nzbozorg/nzvpn` را می‌خواند و در تنظیمات فعلی گزینه‌ای برای تغییر نشانی مخزن ندارد.
برای نسخهٔ شخصیِ ساخته‌شده از سورس، `BrandConfig.DEFAULT_REPO` را می‌توانید
با یکی از شکل‌های زیر جایگزین کنید:

| چیزی که وارد می‌کنید | اپ چه می‌کند |
|---|---|
| `https://github.com/nzbozorg/nzvpn` | خودش `raw.githubusercontent.com/...` را امتحان می‌کند |
| `https://raw.githubusercontent.com/nzbozorg/nzvpn/main/configs.txt` | همان را مستقیم می‌خواند |
| `https://gist.githubusercontent.com/...` | مستقیم می‌خواند |
| `https://cdn.jsdelivr.net/gh/nzbozorg/nzvpn@main/configs.txt` | مستقیم می‌خواند |
| مخزن خصوصی | پشتیبانی نمی‌شود؛ برنامه توکن گیت‌هاب یا بخش تنظیمات ندارد |

اگر فقط نام مخزن را بدهید (`nzbozorg/nzvpn`)، اپ روی شاخهٔ `main` و بعد `master` این نام‌ها را
به‌ترتیب امتحان می‌کند:

```
configs.txt · nzconfigs.txt · nzvpn.txt · v2ray.txt · sub.txt
```

## ۲) قواعد خطوط فایل

| خط | نتیجه |
|---|---|
| خالی | نادیده |
| شروع با `#` یا `//` | نظر (نادیده) |
| `sub: <آدرس>` یا `sublink:` یا `sub-url:` | محتوای آن آدرس دانلود و باز می‌شود |
| `https://...` (بدون `sub:`) | همان «ساب» فرض می‌شود (بسیاری از پنل‌ها لینک خام می‌دهند) |
| `vmess://` `vless://` `trojan://` `ss://` `socks://` `http://` | یک سرور به لیست اضافه می‌شود |
| `v2ray://...?url=...` | آدرس داخل پارامتر `url` به‌عنوان ساب خوانده می‌شود |
| متن Base64 طولانی (بدون `://`) | مثل ساب باز می‌شود (پشتیبانی از فایل سابِ خام) |

هر خط را می‌توانید با `#نام سرور` تمام کنید؛ آن نام، جای نام سرور در برنامه می‌نشیند:

```
trojan://pass@1.2.3.4:443?security=tls#سرور-تهران
```

اگر لینک بدون `#نام` باشد، نام از روی دامنه/آی‌پی ساخته می‌شود (`نام-میزبان:پورت`).

## ۳) پروتکل‌ها و پارامترهای پشتیبانی‌شده

### vless / vmess
- `type`: `tcp` · `ws` · `grpc` · `http` · `h2` · `xhttp` · `quic`
- `security`: `none` · `tls` · `reality`
- `sni` · `fp` · `alpn` · `pbk` · `sid` · `spx` (برای reality)
- `path` · `host` · `serviceName` (برای ws/grpc)
- `flow=xtls-rprx-vision` (برای vless روی tcp+tls/reality)
- vmess به‌صورت `vmess://<base64(json)>` با کلیدهای v2rayN:
  `v, ps, add, port, id, aid, scy, net, type, host, path, tls, sni, alpn, fp`

### trojan
- `security=tls` یا `none` · `type` · `path` · `host` · `sni` · `fp` · `alpn`

### shadowsocks
- SIP002: `ss://base64(method:password)@host:port#name`
- شکل قدیمی: `ss://base64(method:password@host:port)#name`
- روش‌ها: `aes-128-gcm` · `aes-256-gcm` · `chacha20-ietf-poly1305` · `xchacha20-ietf-poly1305` · `none` و …
- `plugin=obfs-local;obfs=http` و `v2ray-plugin` هم خوانده می‌شوند
  (اجرای پلاگین‌های باینری داخل این نسخه فعال نیست؛ فقط پارامترها نگه داشته می‌شوند)

### socks / http
- `socks://base64(user:pass)@host:port#name`
- `http://...` لینک کانفیگ (لینک وب‌سایت را در فایل نگذارید!)

## ۴) نمونهٔ کامل

```text
# ------------------------- ساب‌ها -------------------------
sub: https://panel.example.com/sub/9f8e7d6c
https://panel2.example.com/sub/abcd1234

# ------------------------- سرورهای مستقیم ----------------
vless://b0c8a5d2-a5a2-47b6-8c12-4af1cfb0f310@de1.example.com:443?encryption=none&security=reality&sni=www.microsoft.com&fp=chrome&pbk=jNXHt1yRo0vDuchQlIP6Z0ZvjT3KtzVI-T4E7RoLJS0&sid=6ba85179e30d4fc2&spx=%2F&type=tcp&flow=xtls-rprx-vision#آلمان-ریلیتی
trojan://Tr0janPass@nl1.example.com:8443?security=tls&type=ws&path=%2Fnz&host=nl1.example.com#هلند-تروجان
vmess://eyJ2IjoiMiIsInBzIjoiVFYiLCJhZGQiOiJ0djEuZXhhbXBsZS5jb20iLCJwb3J0IjoiNDQzIiwiaWQiOiIxMTExMTExMS0yMjIyLTMzMzMtNDQ0NC01NTU1NTU1NTU1NTUiLCJhaWQiOiIwIiwic2N5IjoiYXV0byIsIm5ldCI6IndzIiwidHlwZSI6Im5vbmUiLCJob3N0IjoidHYxLmV4YW1wbGUuY29tIiwicGF0aCI6Ii92bWVzcyIsInRscyI6InRscyIsInNuaSI6InR2MS5leGFtcGxlLmNvbSJ9
ss://YWVzLTI1Ni1nY206cGFzc3dvcmQxMjM=@tr1.example.com:8388#ترکیه

# ------------------------- تمیزکاری -----------------------
// این خط هم نظر است
```

## ۵) خطاهای رایج

| مشکل | علت |
|---|---|
| لیست خالی | فایل هنوز در مخزن نیست، یا نامش جزء نام‌های بالا نیست |
| «فایل پیدا نشد» | شاخه اشتباه است (اپ `main` و `master` را امتحان می‌کند) |
| «فایل بزرگ‌تر از حد مجاز» | حجم فایل بیش از ۴ مگابایت است |
| «خط نامعتبر» | لینک شکسته یا ناقص است؛ بقیهٔ خطوط سالم خوانده می‌شوند |
| سرور وصل می‌شود ولی اینترنت نیست | کانفیگ سرور خراب است — با `flow`/`sni`/`pbk` اشتباه معمولاً همین می‌شود |

> برای اطمینان از سالم بودن لینک‌ها، فایل را داخل NZVPN وارد کنید و تست تأخیرِ پوشهٔ مربوط را بزنید.
