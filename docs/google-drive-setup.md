# Google Drive backup: one-time Google Cloud setup

The app talks to Google Drive through an OAuth client registered in Google Cloud. Until one exists for
this app, **Settings → Connect Google Drive** fails with "This copy of the app isn't registered with
Google Cloud yet". No client ID or secret goes into the code: Google recognises the app by its package
name and the certificate it's signed with.

You need a Google account and the release keystore (`release.jks` + `keystore.properties`).

## 1. Create a project and turn on the Drive API

1. Open the [Google Cloud console](https://console.cloud.google.com/) and create a project, for example `Kharcha`.
2. Go to **APIs & Services → Library**, search for **Google Drive API** and click **Enable**.

## 2. Consent screen (Google Auth Platform)

1. **Branding**: app name `Kharcha`, your support email, developer contact email. Leave the logo
   empty: an uploaded logo makes Google verify the brand before the app can be published.
2. **Audience**: user type **External**.
3. **Data Access → Add or remove scopes**: add
   `https://www.googleapis.com/auth/drive.appdata` ("See, create, and delete its own configuration data
   in your Google Drive"). It's the only scope the app asks for, and Google classes it as non-sensitive.
4. **Audience → Publish app** so the status reads **In production**. While it says *Testing*, only the
   test users you list can connect, and Google drops the access after 7 days: the daily sync then stops
   and Settings asks you to "Allow access" again every week. Because `drive.appdata` is non-sensitive,
   publishing doesn't need Google's sensitive-scope review.

## 3. Android OAuth clients

Create one **Android** client per signing key under **Clients → Create client → Android application**:

| Field | Value |
| --- | --- |
| Package name | `com.amir.expense` |
| SHA-1 certificate fingerprint | see below |

Release key (the one every published APK is signed with):

```bash
keytool -list -v -keystore release.jks -alias <keyAlias from keystore.properties>
```

Debug key, if you also want Drive sync in `./gradlew assembleDebug` builds (a second client):

```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android
```

Copy the `SHA1:` line. `./gradlew signingReport` prints both as well.

## 4. Try it

Install the APK, open **Settings → Google Drive backup → Connect Google Drive**, pick your account and
allow access. The card then shows your email and "Last synced today". It syncs again every day when
the phone is online, and **Sync now** syncs right away.

After uninstalling and reinstalling, connect the same Google account: while the app is still empty it
restores the backup by itself. If the phone already has data that differs from the backup, the card asks
whether to **Restore it** or **Replace it** with the phone's data. It never overwrites a backup it
didn't write without asking.

## Where the backup lives

One file, `kharcha-backup.json`, in the Drive *app data folder*: hidden from your Drive file list,
readable only by this app, and kept when the app is uninstalled. It holds payments, categories,
budgets, auto-file rules and import history. It doesn't hold the statement password. To delete it:
drive.google.com → Settings (gear) → **Manage apps** → Kharcha → **Options → Delete hidden app data**.

## Troubleshooting

| What you see | Cause |
| --- | --- |
| "isn't registered with Google Cloud yet" | No Android client matches the installed APK: check the package name and that the SHA-1 is from the key that signed this APK (debug and release differ). |
| "Google Drive API has not been used in project…" | The Drive API isn't enabled (step 1). |
| "Google needs you to allow access to Drive again" every week | The consent screen is still in *Testing* (step 2.4). |
| "Access blocked: app has not completed the Google verification process" | Testing mode and your account isn't a test user, or a logo was uploaded (brand verification pending). |
