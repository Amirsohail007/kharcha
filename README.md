# Expenses

Personal Android app: set a monthly budget, import PhonePe statements, file each payment into a category, see what's left.

PhonePe has no API for personal history, so the app reads the statement PDF
(PhonePe → History → Download Statement; the password is your PhonePe mobile number).
Share the PDF to **Expenses**, or use **Import PhonePe statement** in the app.
Re-importing an overlapping statement only adds new payments.

## Build and install

```bash
./gradlew assembleDebug
```

Install on your phone over USB (Developer options → USB debugging on):

```bash
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or send `app/build/outputs/apk/debug/app-debug.apk` to the phone and open it (allow "install unknown apps").

## Check the parser against a real statement

Statements from different PhonePe versions are laid out differently. To check yours without a phone:

1. Put the statement PDF in `samples/` (gitignored).
2. Put the password in `samples/password.txt`.
3. Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*SampleStatementTest*' -i
```

It prints the parsed transactions and writes the extracted text to `samples/<name>.txt`.
If nothing parses, that text file shows what `PhonePeParser` has to handle.

## How the numbers work

- Money received is not counted until you file it under a category, where it counts as a refund.
- "Not an expense" (self-transfers, lending) is excluded from every total.
- Payments not yet categorized still count against the monthly total.
- A budget applies from the month you set it onward; changing it never rewrites past months.
- Budget alerts fire once at 80% and once at 100%, for the current month only.
