# Signing key (one-off setup)

Android only installs an update if it is signed with the **same key** as the installed app.
Create the key once, back it up in two places, and give it to GitHub Actions as secrets.
If you lose it you must uninstall the app (losing local data) to install a build signed with a new key.

## 1. Create the keystore

Needs a JDK (`keytool`). Easiest without installing anything: open a GitHub Codespace on this repo, or
install Temurin 17.

```bash
keytool -genkeypair -v -keystore allbanks-release.jks -alias allbanks \
  -keyalg RSA -keysize 4096 -validity 10000
```

Choose a strong password. **Never commit the .jks file** (it is git-ignored).

## 2. Add repository secrets

GitHub → repo → Settings → Secrets and variables → Actions → New repository secret:

| Name | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | output of `base64 -w0 allbanks-release.jks` (PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("allbanks-release.jks"))`) |
| `ANDROID_KEYSTORE_PASSWORD` | the keystore password |
| `ANDROID_KEY_ALIAS` | `allbanks` |
| `ANDROID_KEY_PASSWORD` | the key password |

## 3. Release

Every push to `main` runs tests, builds a signed APK and publishes a GitHub Release containing
`all-banks.apk` and `version.json`. The app's **Update** button reads
`https://github.com/sh11fty/all-banks/releases/latest/download/version.json`.

This works only while the repository is **public** (a private repo's release assets need a token).
The repo contains no secrets, so public is fine. The Endute API key is never in the repo: you paste it
into the app and it is stored in the Android Keystore.
