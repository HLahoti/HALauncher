# Contributing to HALauncher

Thanks for wanting to help! Bug reports, feature ideas, translations and code are all
welcome.

## Reporting bugs and suggesting features

Open an [issue](https://github.com/HLahoti/HALauncher/issues). For bugs, please include:

- your phone model and Android version,
- the HALauncher version (Settings → About, or the APK you installed),
- what you did, what you expected, and what happened instead,
- a screenshot or screen recording if it helps.

For larger features, please open an issue to discuss the idea before writing code, so
your effort doesn't go into something that conflicts with the app's direction.

## Making a change

1. Fork the repository and create a branch from `main`.
2. Build and run the app (see [Building](README.md#building)).
3. Keep each pull request focused on one change.
4. Test on a device or emulator — including a **release** build
   (`./gradlew assembleRelease`) if your change touches anything R8 might strip
   or rename, such as reflection.
5. Open a pull request describing what changed and why, with screenshots for UI changes.

### Style

- Kotlin and Jetpack Compose, following the existing code style in the file you touch.
- Keep the app lightweight: avoid adding dependencies unless there's a strong reason.
- HALauncher has **no internet permission** and collects no data. Changes that add
  network access, analytics or tracking won't be accepted.
- User-facing text should be short and plain.

## License of contributions

HALauncher is licensed under the [GNU GPL v3.0 or later](LICENSE). By submitting a
pull request, you agree that your contribution is licensed under the same terms, and
that you have the right to submit it.
