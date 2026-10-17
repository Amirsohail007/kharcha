# Releasing a new version

GitHub Actions builds and signs the APK (`.github/workflows/release.yml`) and creates a **draft** release
with it. Drafts are private: the README's download link keeps serving the current version until you
publish the draft.

## One-time setup: the signing key as repository secrets

Every release must be signed with the same key (`release.jks`), or it won't install over earlier versions.
The workflow reads that key from four secrets. On github.com open the repository, then
**Settings → Secrets and variables → Actions → New repository secret**, and add:

| Secret | Value |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | The keystore as text: on your Mac run `base64 -i release.jks \| pbcopy` and paste. |
| `RELEASE_STORE_PASSWORD` | `storePassword` from `keystore.properties` |
| `RELEASE_KEY_ALIAS` | `keyAlias` from `keystore.properties` |
| `RELEASE_KEY_PASSWORD` | `keyPassword` from `keystore.properties` |

GitHub stores secrets encrypted and hides them in logs. Only workflows in this repository can read them,
never pull requests from forks. The workflow also refuses to release an APK whose signing certificate
isn't the one in the README, so a wrong key can't slip through.

Keep `release.jks` and `keystore.properties` backed up on your Mac as before. If you'd rather not put the
key on GitHub, skip this and use [Building on your Mac](#building-on-your-mac) instead.

## Each release

1. Bump `versionCode` (must increase) and `versionName` in `app/build.gradle.kts`.
2. Write the release notes in `docs/releases/v<versionName>.md`. The workflow adds the APK's SHA-256.
3. Get it onto `main` and check that the **Build** workflow passed (Actions tab).
4. **Actions → Release → Run workflow** on `main`. It runs the unit tests, builds and signs the APK, checks
   the certificate, and creates the draft release `v<versionName>` with `kharcha.apk` attached.
   Pushing a tag `v<versionName>` runs the same workflow.
5. Open the draft on the **Releases** page, download `kharcha.apk` on your phone and install it over the
   version you have. Open the app and check your data is still there.
6. **Publish release.** Publishing creates the tag, and the download link now serves the new APK.

Before the first release with Google Drive backup (1.1.0): register the release key in Google Cloud
([docs/google-drive-setup.md](google-drive-setup.md)), or "Connect Google Drive" fails for everyone.
Try it on the draft's APK before publishing.

## Building on your Mac

Without the secrets, the same steps by hand:

```bash
./gradlew testDebugUnitTest assembleRelease
cp app/build/outputs/apk/release/app-release.apk kharcha.apk
shasum -a 256 kharcha.apk
git tag v1.1.0 && git push origin v1.1.0
```

Then on github.com create a release for the tag, titled `Kharcha 1.1.0`, with the notes from
`docs/releases/v1.1.0.md` followed by `APK SHA-256: <hash>`, and attach `kharcha.apk`. The file name must
be exactly `kharcha.apk`: the README links to `releases/latest/download/kharcha.apk`.
Pushing that tag also starts the Release workflow, which fails at the signing step without the secrets.
That's harmless; ignore it.
