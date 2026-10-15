#!/usr/bin/env bash
# ---------------------------------------------------------------------------
#  ارسال فایل در تلگرام با Bot API — NZVPN
#
#  دستورها:
#     bash tools/telegram.sh wait                 ← منتظر اولین پیام به ربات می‌ماند و chat_id را ذخیره می‌کند
#     bash tools/telegram.sh send <فایل> [کپشن]   ← فایل را به همان چت می‌فرستد (پیش‌فرض: تازه‌ترین APK)
#     bash tools/telegram.sh msg  <متن>            ← فقط پیام متنی می‌فرستد
#     bash tools/telegram.sh me                   ← اطلاعات ربات
#
#  توکن در telegram.config ذخیره می‌شود (خارج از گیت — در .gitignore هست)
# ---------------------------------------------------------------------------
set -uo pipefail
cd "$(dirname "$0")/.."
CONF="telegram.config"
API="https://api.telegram.org/bot"

load_conf() {
  [ -f "$CONF" ] || { echo "telegram.config نیست؛ اول اجرا کنید: bash tools/telegram.sh wait" >&2; exit 1; }
  # shellcheck disable=SC1090
  source "$CONF"
  [ -n "${TG_TOKEN:-}" ] || { echo "TG_TOKEN در telegram.config خالی است" >&2; exit 1; }
}

jget() { python3 -c "import json,sys;d=json.load(sys.stdin);print(eval('d$1'))" 2>/dev/null; }

cmd="${1:-help}"
case "$cmd" in

  me)
    load_conf
    curl -s --max-time 30 "${API}${TG_TOKEN}/getMe" | python3 -m json.tool
    ;;

  wait)
    load_conf
    echo "منتظر پیام به ربات @${TG_BOT_USERNAME:-…} هستم (هر ۲۵ ثانیه چک می‌کنم)…"
    OFFSET=0
    for i in $(seq 1 240); do
      RESP="$(curl -s --max-time 40 "${API}${TG_TOKEN}/getUpdates?timeout=25&offset=${OFFSET}")"
      CHAT="$(printf '%s' "$RESP" | jget "['result'][-1]['message']['chat']['id']" 2>/dev/null)"
      UPD="$(printf '%s' "$RESP" | jget "['result'][-1]['update_id']" 2>/dev/null)"
      if [ -n "$CHAT" ] && [ "$CHAT" != "None" ]; then
        NAME="$(printf '%s' "$RESP" | python3 -c "
import json,sys
d=json.load(sys.stdin)
m=d['result'][-1].get('message',{})
c=m.get('chat',{})
print((c.get('first_name') or '')+' '+(c.get('last_name') or ''), '|', c.get('username') or '-', '|', c.get('type'))
" 2>/dev/null)"
        grep -v '^TG_CHAT_ID=' "$CONF" > "$CONF.tmp" 2>/dev/null || true
        { cat "$CONF.tmp" 2>/dev/null; echo "TG_CHAT_ID=$CHAT"; echo "TG_CHAT_NAME=$NAME"; } > "$CONF"
        rm -f "$CONF.tmp"
        echo "✓ chat_id پیدا شد: $CHAT  ($NAME)"
        exit 0
      fi
      [ -n "$UPD" ] && [ "$UPD" != "None" ] && OFFSET=$((UPD + 1))
    done
    echo "✗ تا این لحظه پیامی از کسی به ربات نرسیده." >&2
    exit 1
    ;;

  send)
    load_conf
    FILE="${2:-}"
    if [ -z "$FILE" ]; then
      FILE="$(ls -t app/build/outputs/apk/debug/*arm64*.apk apk/*.apk 2>/dev/null | head -1)"
    fi
    [ -f "$FILE" ] || { echo "فایل پیدا نشد: $FILE" >&2; exit 1; }
    [ -n "${TG_CHAT_ID:-}" ] || { echo "TG_CHAT_ID خالی است؛ اول: bash tools/telegram.sh wait" >&2; exit 1; }
    CAPTION="${3:-📦 NZVPN — نصب: فایل را روی گوشی باز کنید و اجازهٔ نصب بدهید.}"
    # تلگرام بیشتر از ۱۰۲۴ کاراکتر کپشن قبول نمی‌کند
    if [ "${#CAPTION}" -gt 1000 ]; then
      CAPTION="$(printf '%s' "$CAPTION" | cut -c1-980)"
      CAPTION="${CAPTION}…"
    fi
    SIZE="$(du -h "$FILE" | cut -f1)"
    echo "→ ارسال $(basename "$FILE") ($SIZE) به chat_id=$TG_CHAT_ID"
    curl -s --max-time 300 \
      -F "chat_id=${TG_CHAT_ID}" \
      -F "document=@${FILE}" \
      -F "caption=${CAPTION}" \
      "${API}${TG_TOKEN}/sendDocument" \
      | python3 -c "
import json,sys
d=json.load(sys.stdin)
if d.get('ok'):
    r=d['result']
    print('✓ ارسال شد — message_id:', r.get('message_id'), '| نام فایل:', r.get('document',{}).get('file_name'), '| حجم:', r.get('document',{}).get('file_size'))
else:
    print('✗ خطا:', d.get('error_code'), d.get('description'))
    sys.exit(1)
"
    ;;

  msg)
    load_conf
    [ -n "${TG_CHAT_ID:-}" ] || { echo "TG_CHAT_ID خالی است" >&2; exit 1; }
    curl -s --max-time 60 -F "chat_id=${TG_CHAT_ID}" -F "text=${2:?متن را بدهید}" \
      "${API}${TG_TOKEN}/sendMessage" | python3 -c "
import json,sys
d=json.load(sys.stdin)
print('✓' if d.get('ok') else '✗ ' + str(d.get('description')))
"
    ;;

  *)
    sed -n '2,14p' "$0"
    ;;
esac
