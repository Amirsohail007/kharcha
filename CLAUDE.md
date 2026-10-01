# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Kharcha: single-module Android app (Kotlin, Jetpack Compose, Room). Package and applicationId are
`com.amir.expense` and the launcher label is "Expenses". Never change the applicationId: installed
copies would stop receiving updates.

## Commands

```bash
./gradlew assembleDebug                      # debug APK -> app/build/outputs/apk/debug/
./gradlew testDebugUnitTest                  # all JVM unit tests (no instrumentation tests exist)
./gradlew :app:testDebugUnitTest --tests 'com.amir.expense.PhonePeParserTest'                 # one class
./gradlew :app:testDebugUnitTest --tests 'com.amir.expense.BudgetMathTest.alertLevels'        # one test
./gradlew :app:testDebugUnitTest --tests '*SampleStatementTest*' -i   # parse real PDFs in samples/
./gradlew :app:lintDebug                     # Android lint (AGP default, no custom config)
./gradlew assembleRelease                    # signed, R8-shrunk APK (needs keystore.properties)
```

`adb` is not on PATH: use `~/Library/Android/sdk/platform-tools/adb`. The local test emulator is AVD
`expense_test`. On a debug build, inspect the database with
`adb shell run-as com.amir.expense sqlite3 databases/expenses.db`. Release builds are not debuggable,
so `run-as` fails there.

## Build setup gotchas

- AGP 9.4 has Kotlin built in. Do not apply `org.jetbrains.kotlin.android`. The Compose compiler
  plugin version (`org.jetbrains.kotlin.plugin.compose`) pins the Kotlin version. Room uses KSP.
- Release builds run R8 (`isMinifyEnabled`, `isShrinkResources`) with `app/proguard-rules.pro`, and
  drop `org/bouncycastle/pqc/**`. A new library that uses reflection can work in debug and break in
  release, so install the release APK and exercise the feature before shipping.

## Architecture

Data flows one way. Room DAO `Flow`s go to `Repository` (`data/`), then to `MainViewModel`
StateFlows, then to Compose screens via `collectAsState`. `App` builds the database and the
`Repository` singleton. There is no DI and no navigation library: `ui/AppRoot.kt` switches tabs with
an enum plus `BackHandler`, and every screen takes the single `MainViewModel`.

- **Month on screen:** `vm.month` drives `txns`, `prevTxns` and `budgets` through `flatMapLatest`.
  Screens read `month` and `txns` separately, and `txns` lags a frame after a month switch. Any code
  that indexes by day of month must filter to the current month first (see `DailyBars` in
  `InsightsScreen.kt`). `refreshMonth()` follows the calendar month on resume.
- **Money:** `Long` paise everywhere. Use `formatRupees` and `parseRupees` from `data/Money.kt`.
  Sign carries meaning: `amountPaise > 0` is money out, `< 0` is money in.
- **Totals (`data/BudgetMath.kt`, shared by UI and alerts):** the monthly total counts every
  non-ignored transaction, including uncategorized ones. Category spend rolls children into their
  parent. Imported credits arrive with `ignored = true`. Filing a credit under a category un-ignores
  it, so it subtracts there as a refund.
- **Budgets are effective-dated:** a `budget` row is `(yearMonth, categoryId)`, and a month uses the
  latest row at or before it. Amount 0 means "removed", and `categoryId 0` (`Budget.TOTAL`) is the
  monthly total. `vm.setBudget` writes for the month on screen, so past months never change.
- **Alerts:** every `Repository` mutation calls `BudgetAlerts.check(month)`. It only fires for the
  current calendar month, and the `alert_fired` table makes each 80% / 100% alert fire once per month
  per budget.
- **Import pipeline:** `importer/PdfText` (pdfbox-android, password = PhonePe mobile number) feeds
  `importer/PhonePeParser`, which is pure and JVM-testable, then `Repository.import`.
  - The parser splits text on `MMM d, yyyy` dates and searches each record for its fields, because
    field order varies between statement versions. Records without a Transaction ID are skipped.
  - Dedupe is the unique `externalId` (Transaction ID) with insert-IGNORE.
  - Auto-file rules apply to debits only.
  - The last password is kept in SharedPreferences `settings` / `pdfPassword`.
  - Statements arrive by share or view intent (`MainActivity.handleShare`) or the `OpenDocument` picker.
- **Auto-file rules:** keyed by `merchantKey` (normalized merchant name). Only the "Always file here"
  checkbox in `CategorizeSheet` creates a rule; it also files that merchant's other inbox items. The
  Inbox one-tap chips deliberately file just the one payment.
- **Categories:** two levels, seeded in `AppDatabase` `onCreate` from `SEED`. `ui/CategoryStyle.kt`
  maps colors and icons by seed id: ids are 1..N in `SEED` order, which is why renames keep their
  style. Never reorder `SEED` or insert into the middle of it; append only. User-created categories
  fall back to name lookup, then neutral gray.
- **Schema:** `AppDatabase` is version 1 with `exportSchema = false` and no migrations. v1.0.0 is
  public, so any entity change needs a version bump and a `Migration`. Otherwise existing users'
  apps crash on upgrade.

## UI conventions

The design system is "Indigo fintech": Plus Jakarta Sans, white cards on a gray canvas, and the
`HeroIndigo` hero card. Build new UI from `Theme.kt` colors and the shared pieces in
`Components.kt` and `CategoryStyle.kt` (`AppCard`, `ScreenHeader`, `MonthSwitcher`, `CategoryBadge`,
`lookOf`, `CategoryPickerSheet`). Category hues come from a colorblind-validated 8-slot palette. Check
new screens in both light and dark mode on the emulator. The XML themes (`values/` and
`values-night/themes.xml`) exist only for the launch window and platform dialogs like
`DatePickerDialog`; everything else is Compose.

## Tests

JVM unit tests only. `NumberFormat` on the JVM lacks Indian lakh grouping, so don't assert formatted
rupee strings above ₹99,999. `SampleStatementTest` reads `samples/*.pdf` with the password in
`samples/password.txt`, writes the extracted text to `samples/<name>.txt`, and is skipped when no PDF
is present. `samples/` is gitignored.

The parser has only been verified on synthetic statements. When a real PhonePe PDF is available,
run `SampleStatementTest` first and adapt `PhonePeParser` to the extracted text.

## Releasing

1. Bump `versionCode` (must increase) and `versionName` in `app/build.gradle.kts`.
2. `./gradlew assembleRelease`. Signing reads `keystore.properties` and `release.jks` at the repo
   root. Both are gitignored and must be the same key forever, or updates won't install over
   existing copies.
3. Commit, tag `vX.Y.Z`, and push the branch and the tag.
4. Create a GitHub release for the tag and attach the APK named exactly `kharcha.apk`. The README
   links to `releases/latest/download/kharcha.apk`. Put the APK's SHA-256 in the release notes. `gh`
   is not logged in on this machine, so the release is created on github.com.

Commits must use the personal GitHub identity (noreply email), never the work email. The global
gitconfig's `includeIf` for `~/Documents/amirsohail007/` sets this; check `git config user.email`
before committing.
