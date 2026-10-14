# Publishing HALauncher to the Google Play Store — checklist

Everything needed for a **direct-share build** is already done (signed APK/AAB in
`dist/`). This is the list of things left to do **before** you can publish on Play.
Work top to bottom.

## 1. Fix the application ID (must do first — it's permanent)

Google Play **rejects `com.example.*`**. The current app ID is
`com.example.halauncher`. Pick a real, permanent ID (it can never change after your
first upload) and set it in `app/build.gradle.kts`:

```kotlin
defaultConfig {
    applicationId = "com.harshlahoti.halauncher"  // <- choose yours
    ...
}
```

Suggestions: `com.harshlahoti.halauncher`, `dev.harshlahoti.halauncher`, or
`io.github.harshlahoti.halauncher`.

- `applicationId` is the only field Play checks — you do **not** have to change the
  Kotlin `namespace` (`com.example.halauncher`), though you may for tidiness.
- If you already sideloaded the current APK to testers, changing the ID means the
  Play version installs as a *separate* app for them (different identity). Fine for a
  small test group; just be aware.
- After changing it, rebuild the AAB (see step 7).

## 2. Create a Google Play Developer account

- Go to <https://play.google.com/console>, sign up.
- **One-time US$25 registration fee.**
- Since 2023, new personal accounts must complete **identity verification** (name,
  address, phone) and, for personal accounts, a **closed test with 12+ testers for
  14 days** before you can apply for production access. Budget time for this.

## 3. Decide on Play App Signing (recommended: yes)

- Opt into **Play App Signing** (default). You upload with your *upload key*
  (`halauncher-release.jks`), and Google holds the real app-signing key.
- Benefit: if you lose your upload key, Google can reset it. Keep backing up the
  keystore regardless.

## 4. Review permissions & data safety (this app needs attention here)

The manifest requests some sensitive permissions that Play scrutinizes:

- **`MANAGE_EXTERNAL_STORAGE` ("All files access")** — Play requires a special
  declaration and a documented justification for this. Launchers that surface files
  in search can qualify, but you must explain it in the console, and it may trigger a
  manual review. Confirm you still need it; if file-search isn't essential, dropping
  it makes approval much easier.
- **`READ_CONTACTS`** — must be justified in the Data Safety form.
- **Device Admin (force-lock)** — allowed, but described in the listing may draw
  review; the app already scopes it to force-lock only.
- Fill out the **Data Safety form** (what data is accessed, whether it leaves the
  device — for this app, nothing is collected/transmitted, which is the easy case).
- Add a **Privacy Policy URL** (required when requesting sensitive permissions). A
  simple hosted page stating "no data is collected or shared" is enough.

## 5. Prepare store listing assets

- **App name:** HALauncher (or a public-facing name).
- **Short description** (≤80 chars) and **full description** (≤4000 chars).
- **App icon:** 512×512 PNG (32-bit, with alpha).
- **Feature graphic:** 1024×500 PNG/JPG.
- **Screenshots:** at least 2 phone screenshots (min 320px, max 3840px per side).
  Grab these from a device/emulator running the release build.
- **Category:** Personalization. **Content rating:** complete the questionnaire.
- **Target audience** and ads declaration (this app has no ads).

## 6. Confirm target SDK & compliance

- `targetSdk = 37` — Play requires targeting a recent API level for new apps; 37 is
  ahead of the current minimum, so you're fine.
- Test the **R8/minified release build** on a real device before uploading — obfuscation
  can surface issues that debug builds don't. Install `dist/HALauncher.apk` and
  exercise search, the app grid, contacts, file search, and double-tap-to-lock.

## 7. Build & upload

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:bundleRelease
# -> app/build/outputs/bundle/release/app-release.aab
```

- Upload the **`.aab`** (not the APK) to a release track: **Internal testing** first,
  then Closed → Open → Production.
- Upload the **`mapping.txt`** (`dist/HALauncher-1.4-mapping.txt`) so Play can
  de-obfuscate crash reports.
- For every future release, **increase `versionCode`** (e.g. 2, 3, …) and update
  `versionName`. Play refuses an upload whose versionCode isn't higher than the last.

## 8. Roll out

- Start with Internal/Closed testing, confirm installs and behavior, then promote to
  Production. First production review can take a few days.

---

### Quick reference — current build state
- versionName: `1.4`, versionCode: `5`
- applicationId: `com.example.halauncher` ← **must change before Play (step 1)**
- Signing: release keystore `halauncher-release.jks` (git-ignored; back it up)
- Min SDK 24 (Android 7.0), Target SDK 37
