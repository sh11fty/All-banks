# All Banks

Android app that merges your Endute Connect accounts into one view: a single balance, one
transaction list with per-bank chips, internal transfers linked and excluded from spending, and
rule-based categories. Everything is stored encrypted on the phone. No Play Store, no cloud copy.

## What it does

- **Balance**: sum of booked balances of all included accounts (cards/loans negative, savings toggle).
- **Activity**: merged, searchable list; filter by bank; tap to categorise.
- **Internal transfers**: debit and credit of equal amount in two of your accounts within 3 days are
  linked. Endute does not expose counterparty sort code / account number, so matching uses amount,
  date and text hints (institution, account name, last four digits, end-to-end id). Strong matches
  auto-link; ambiguous ones wait in **Review**. Wrong links can be undone.
- **Categories and rules**: categorise once, tick "also categorise future matches" and a rule is
  created. Rules support contains / equals / regex, direction, amount range. Endute's own enrichment
  category is used when no rule matches. Manual choices are never overwritten.
- **Sync**: background every ~6 h (WorkManager) plus a Refresh button. Overlapping 7-day window, dedupe
  on Endute transaction id.
- **Alerts**: notification when consent is due within 10 days or a connection is expired/suspended.
- **Security**: SQLCipher database, passphrase and API key wrapped by an Android Keystore key,
  biometric / device-credential lock, cloud backup disabled, encrypted export/restore.
- **Updates**: Settings → Check for update → download, SHA-256 verified, Android installer.

## Known limits of the Endute API (checked against their docs)

- No pending flag: only booked transactions are shown.
- No manual "refresh from bank" call: data is as fresh as Endute's sync schedule (a few times a day).
- Amounts are treated as 2-decimal currencies; cross-currency transfer matching is not implemented.

## Setup

1. Add the signing secrets: see [docs/SIGNING.md](docs/SIGNING.md).
2. Push to `main`; GitHub Actions publishes the APK under Releases.
3. On the phone open the repo's latest release, download `all-banks.apk`, allow install from your
   browser, install. Open the app, paste your Endute API key in Settings, press Refresh.
4. Later updates: Settings → Check for update.

Build locally (needs JDK 17, Android SDK, Gradle 8.9):
`gradle :app:testDebugUnitTest :app:assembleDebug`
