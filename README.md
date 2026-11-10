# HALauncher

A home screen that gets out of your way. Swipe up to find any app, contact, file or
shortcut. Spin your favourite apps on a sphere. Type a sum and get the answer. No
clutter, no ads, no account — and no internet permission, so nothing leaves your phone.

**Website & download:** <https://hlahoti.github.io/HALauncher/> ·
[All releases](https://github.com/HLahoti/HALauncher/releases)

## Features

- **Search** — one swipe finds apps, contacts, files and app shortcuts in a single list.
- **App shortcuts** — the actions apps hide behind a long-press show up in search.
- **Quick maths** — type a sum into search and see the result as you type.
- **App sphere** — spin through your eight pinned apps and tap one to open it.
- **Home screen** — clock, date and quick buttons for everyday apps.
- **Hands-on tour** — learn every gesture on your real home screen.
- **Private** — no internet access; contacts and files permissions are optional.

## Building

Requirements: a recent Android Studio (or the Android SDK + command line tools).
Gradle downloads the right JDK toolchain automatically.

```sh
git clone https://github.com/HLahoti/HALauncher.git
cd HALauncher
./gradlew assembleDebug        # APK in app/build/outputs/apk/debug/
./gradlew installDebug         # install on a connected device or emulator
```

The app requires Android 7.0 (API 24) or newer.

Official releases are signed with a private key that is not part of this repository.
Without it, `./gradlew assembleRelease` produces an unsigned APK. To sign with your own
key, create a git-ignored `keystore.properties` in the project root:

```properties
storeFile=my-release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

## Contributing

Bug reports, ideas and pull requests are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md).

## Supporting the project

HALauncher is free and has no ads. If you enjoy it, you can support development by
buying it on the Play Store once it is listed there, or by starring the repo and
telling a friend.

## License

Copyright (C) 2026 Harsh Lahoti

HALauncher is free software: you can redistribute it and/or modify it under the terms
of the GNU General Public License as published by the Free Software Foundation, either
version 3 of the License, or (at your option) any later version. See [LICENSE](LICENSE)
for the full text.

The license covers the source code. The name "HALauncher" and the app icon identify
the official app. If you publish a modified version, please give it a different name
and icon so users aren't confused about which app they are installing.
