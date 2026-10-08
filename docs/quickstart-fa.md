# راهنمای شروع آزمایشگاه IBM MQ

این پروژه مستقل است و تمام کلاس‌های برنامه زیر پکیج `dev.sdxcod.mqlab` قرار دارند. Spring Boot فقط برنامه را اجرا می‌کند، تنظیمات را می‌خواند و کلاس‌ها را به هم وصل می‌کند. ارسال و دریافت مستقیماً با `com.ibm.mq` انجام می‌شود؛ هیچ JMS یا Jakarta Messaging در مسیر ارتباط نیست.

## پیش‌نیاز

Java 21، Docker و Docker Compose نسخهٔ جدید، Bash و OpenSSL لازم است. Maven را wrapper دانلود می‌کند. در ویندوز از WSL2 با دسترسی به Docker Desktop استفاده کنید. ایمیج IBM انتخاب‌شده amd64 است؛ روی Apple Silicon اجرای آن به emulation وابسته است. برای Kubernetes نود amd64 و storage class لازم است.

از داخل پوشهٔ پروژه اجرا کنید:

```bash
./scripts/init-lab.sh
./mvnw -B -ntp verify
docker compose up -d --wait --wait-timeout 360 mq
./scripts/lab.sh demo
```

اسکریپت اول رمز تصادفی محلی می‌سازد و در اجرای بعدی همان رمزها را نگه می‌دارد. این فایل‌ها وارد Git یا Docker image برنامه نمی‌شوند. تنظیم `LICENSE=accept` مربوط به شرایط استفادهٔ IBM MQ Developer است؛ شرایط IBM را قبل از اجرای ایمیج بخوانید.

دستور demo سه نتیجهٔ `PASS` چاپ می‌کند: لغو ارسال، ارسال قطعی و بازگشت پیام دریافت‌شده با backout، و حذف قطعی با commit. پیام فارسی نیز برای آزمایش UTF-8 ارسال می‌شود. demo فقط پیام‌های خودش را با شناسهٔ MQ می‌خواند.

## آزمایش دستی

```bash
./scripts/lab.sh send --lab.queue=in --lab.message='سلام از آزمایشگاه جاوا'
./scripts/lab.sh depth --lab.queue=in
./scripts/lab.sh receive --lab.queue=in --lab.wait=3s
./scripts/lab.sh shell
```

داخل shell این دستورها را اجرا کنید:

```text
connect
put in Hello MQ
status
commit
get in
backout
get in
commit
disconnect
quit
```

پس از put/get عملیات هنوز نهایی نشده است. commit ارسال را نهایی می‌کند یا حذف پیام دریافت‌شده را قطعی می‌کند. backout ارسال را لغو می‌کند یا پیام دریافت‌شده را برمی‌گرداند. این اثر به تمام عملیات باز همان اتصال تعلق دارد. با خروج از shell عملیات باز شناخته‌شده لغو می‌شود.

`isConnected` وضعیت محلی اتصال را می‌دهد؛ بررسی فعال بودن سرور با یک عملیات واقعی مثل inquiry انجام می‌شود. depth تعداد فعلی را از سرور می‌خواند، اما تضمین نمی‌کند همان تعداد پیام برای مصرف‌کننده قابل دریافت باشد.

کلاس‌های اصلی برای مطالعه:

- `transport/NativeMqSession`: توابع اصلی اتصال، صف و تراکنش.
- `service/MessageSender`: ارسال با تعیین commit یا backout.
- `service/MessageReceiver`: دریافت با همان انتخاب.
- `cli/LabRunner`: دستورها، shell و demo.
- `config/MqSettings` و `application.yml`: تنظیمات اتصال.

تست واحد بدون سرور اجرا می‌شود. تست واقعی پس از بالا آمدن MQ:

```bash
./mvnw -B -ntp -Pmq-it verify
```

اجرای برنامه در Docker:

```bash
docker compose --profile app build agent
docker compose --profile app run --rm agent
```
