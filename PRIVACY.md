# Kharcha privacy policy

Last updated: 18 October 2026

Kharcha is an open-source expense tracker for Android. It has no account, no server and no analytics.
This page explains what the app stores and where.

## On your phone

Everything you enter or import stays in a database on your phone: payments, categories, budgets,
auto-file rules and import history. When you import a PhonePe statement, the app reads the PDF on the
phone. The statement password is saved on the phone only, to fill it in next time.

If Android backup is on, Android includes the app's data in your phone's backup to your Google account,
as it does for other apps.

## Google Drive backup (optional)

Drive backup is off until you tap **Connect Google Drive** in Settings and choose a Google account.
Then:

- The app asks for one permission, `drive.appdata`. It lets the app create and read its own file in a
  hidden app folder in **your** Drive. The app can't see or change any of your other Drive files.
- It keeps one file there, `kharcha-backup.json`, holding your payments, categories, budgets, rules and
  import history. The statement password isn't in it.
- The app talks to Google directly from your phone. The developer never receives your data or your
  Google sign-in, and has no server that could.
- The app syncs when you tap **Sync now** and about once a day while connected. Drive backup is the
  only thing the app uses the internet for.

To stop syncing, tap **Disconnect** in Settings. To delete the backup, open drive.google.com →
Settings → **Manage apps** → Kharcha → **Options** → **Delete hidden app data**.

Kharcha's use of information received from Google APIs adheres to the
[Google API Services User Data Policy](https://developers.google.com/terms/api-services-user-data-policy),
including the Limited Use requirements. Your Drive data is used only to back up and restore your own
Kharcha data, and is never shared, sold or used for anything else.

## Contact

Questions or concerns: open an issue at https://github.com/Amirsohail007/kharcha/issues.
