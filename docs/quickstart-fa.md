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

## ارزیابی صف IBM MQ Web Console

باز کردن آدرس زیر در مرورگر:

https://localhost:9443/ibmmq/console

اطلاعات ورود:
- Username: admin
- Password: مقدار فایل .secrets/mqAdminPassword

ممکن است مرورگر بابت گواهی HTTPS هشدار بدهد؛ کنسول محلی از گواهی self-signed استفاده می‌کند.

پس از ورود، Queue Manager با نام QM1 و قسمت Queues را باز کنید. صف‌های آزمایشگاه عبارت‌اند از:

| صف           | کاربرد                             |
|--------------|------------------------------------|
| DEV.LAB.IN   | آزمایش پیام‌های ورودی              |
| DEV.LAB.OUT  | آزمایش پیام‌های خروجی              |
| DEV.LAB.TEST | سناریوی demo و تست‌های integration |

برای مشاهدهٔ اثر دستورها، این آزمایش را انجام دهید:

```bash
./scripts/lab.sh send --lab.queue=in --lab.message='Hello dashboard'
```

سپس اطلاعات صف DEV.LAB.IN را در کنسول refresh کنید و Current depth را ببینید. بعد اجرا کنید:

```shell
./scripts/lab.sh receive --lab.queue=in
```

و دوباره refresh کنید تا اثر دریافت و commit را مشاهده کنید.

کنسول وضعیت صف را نشان می‌دهد؛ تاریخچهٔ دستورهای CLI برنامه را نمایش نمی‌دهد. برای دنبال کردن نتیجهٔ هر دستور، خروجی ترمینال برنامه را کنار کنسول ببینید. برای بررسی خطاهای سمت MQ هم استفاده کنید:

```shell
docker compose logs -f mq
```

برای آزمایش commit/backout از shell تعاملی استفاده کنید؛ تغییر عمق صف در زمان تراکنش‌های باز، به‌تنهایی نشان‌دهندهٔ قطعی شدن ارسال یا دریافت نیست.