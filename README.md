<div align="center">
  <h1>Paperized</h1>
  <p><strong>A wallpaper changer for Android: static or live, on a schedule, from your own albums</strong></p>

  [![License](https://img.shields.io/github/license/totallynotkasai/Paperize?style=flat)](LICENSE)
</div>

---

Paperized is a personal fork of [Paperize](https://github.com/Anthonyy232/Paperize) by Anthony La,
based on Paperize 4.2.0. It fixes a number of bugs and adds features such as set times of day,
night albums, favourites, home-screen widgets and live auto-pan. See the [CHANGELOG](CHANGELOG.md)
for everything that changed.

It is built for personal use and isn't published on any store. The source is here under the same
GPL-3.0 licence as Paperize.

---

## Features

**Albums**
- Albums of individually picked images and whole folders. Folders pick up new files and drop
  deleted ones by themselves (daily, and when the app opens).
- Images rotate in the order shown in the album ("Rotation order"), which you can rearrange, or
  shuffled.
- Mark images as favourites or exclude them from rotation, including images inside folders. Each
  album chooses what its favourites do: a marker only, show them twice as often, or show only them.
- Rename albums, and give an album its own effects that it keeps on every screen.
- Images that Android no longer lets the app read are marked, with a way to grant access again.

**Changing the wallpaper**
- Static wallpapers on the home and lock screens, with the same or different albums. Screens that
  share an album never show the same image.
- A live wallpaper with smooth transitions, parallax, double-tap to change, changes on screen off,
  intervals from one minute, and auto-pan for images that don't fit the screen.
- Change at an interval or at set times of day, and switch to night albums by the clock or with
  the phone's dark theme.
- In static mode, also change when the screen turns off or the phone is unlocked.
- Hold automatic changes until the phone is charging, or pause them in battery saver. Changing by
  hand always works.
- Change by hand from the app, a Quick Settings tile, a launcher shortcut, or the Shuffle Home,
  Shuffle Lock and Shuffle Both widgets.

**Effects**
- Brightness, blur, vignette and grey filter for each screen, plus Fill, Fit, Stretch and None
  scaling.
- Adaptive brightness adjusts the wallpaper to the light or dark theme, and redraws it when the
  theme switches.

**Formats:** JPG, PNG, WEBP, AVIF, HEIC/HEIF, BMP, GIF (first frame) and SVG. TIFF isn't supported,
because Android can't decode it; imports skip unsupported files and say how many.

**Languages:** English and Simplified Chinese.

All images stay where they are on your phone; Paperized only keeps Android's permission to read
them, and stores albums and settings on the device. It has no network access.

---

## Installing

There are no published downloads. Build the app as described below and install the APK with
`adb install` or a file manager.

| Build | App ID | Name on the phone |
|-------|--------|-------------------|
| Release | `com.anthonyla.paperize` | Paperized |
| Debug | `com.anthonyla.paperize.debug` | Paperized Debug |

The release build keeps Paperize's app ID, so it replaces Paperize but can't install over it: it
is signed with a different key. **Uninstall Paperize first, which deletes its albums and settings.**
Android doesn't let one app read another's data and Paperize turns backups off, so albums have to
be created again; your images themselves aren't touched. Debug builds install next to either.

---

## Building from source

### Prerequisites

| Requirement | Version |
|-------------|---------|
| Java | 17 |
| Android Gradle Plugin | 9.3.2 |
| Gradle | 9.7.1 (wrapper included) |
| Compile SDK | 37 (Android 17) |
| Minimum SDK | 31 (Android 12) |
| Target SDK | 36 |

Set `ANDROID_HOME` to your Android SDK, or put `sdk.dir` in `local.properties`. On Windows, use
`gradlew.bat` in place of `./gradlew`.

### Debug build

```bash
./gradlew assembleDebug
```

The APK is in `app/build/outputs/apk/debug/`.

### Release build

Release builds are shrunk with R8 and signed with your own key.

1. Create a key once, and keep it somewhere safe outside the repository. **Back it up:** an app
   signed with it can only be updated by an APK signed with the same key. `keytool` comes with
   Java and asks for the passwords itself.

   ```bash
   keytool -genkeypair -v -keystore ~/keys/paperized-release.jks -alias paperized -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Create `keystore.properties` in the project root. Git ignores it and any `*.jks` file.

   ```properties
   storeFile=C:/Users/you/keys/paperized-release.jks
   storePassword=...
   keyAlias=paperized
   keyPassword=...
   ```

3. Build:

   ```bash
   ./gradlew assembleRelease
   ```

   The signed APK is `app/build/outputs/apk/release/app-release.apk`. Without a key the build
   still works and produces `app-release-unsigned.apk`, which Android won't install.

CI builds use the `SIGNING_KEYSTORE_PATH`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and
`SIGNING_KEY_PASSWORD` environment variables instead, which take precedence over the file. The
GitHub workflow builds and signs releases only when the matching repository secrets are set.

### Tests and checks

```bash
./gradlew clean test
./gradlew lintDebug
```

These are what CI runs on every pull request, along with a check that the exported database
schemas in `app/schemas` are committed.

Device tests need a phone or emulator with Android 12 or newer:

```bash
./gradlew connectedDebugAndroidTest
```

Gradle uninstalls the debug app when they finish. To keep it, install both test APKs
(`assembleDebug assembleDebugAndroidTest`) with `adb install` and run them with
`adb shell am instrument -w com.anthonyla.paperize.debug.test/androidx.test.runner.AndroidJUnitRunner`.
`WallpaperUtilInstrumentedTest` sets the device's wallpaper, and the preferences and scheduler
tests write to the debug app's own settings and jobs.

---

## Architecture

- `AlbumRepository` owns library changes. Imports, reordering, removal, covers and queue updates
  use Room transactions; scans of document providers run outside them. `DocumentSource` keeps
  Android permissions and provider queries apart from the import and refresh use cases.
- `WallpaperRepository` owns the rotation queues and the current-wallpaper records. The rule for
  which images rotate (not excluded, still readable, favourites only when the album says so)
  lives in one place in the database queries.
- `WallpaperChangeRequests` is the one way to ask for a change, from the app, tile, shortcut or
  widgets. `WallpaperChangeService` carries requests out in the foreground and hands them to a
  background job when Android refuses; both go through `WallpaperRequestHandler`.
- `WallpaperController` applies static wallpapers for the service and the scheduled
  `WallpaperChangeWorker`, which hold `WallpaperChangeLock` while applying and rescheduling.
  `WallpaperRenderer` does the image processing.
- `WallpaperScheduler` keeps the interval jobs in step with the settings; set times, the day and
  night switch and dark-theme redraws use their own alarms and jobs in `service/schedule`.
- The live wallpaper shares one queue between all its engines; only the leading engine takes
  images from it. Its decisions live in `LiveEngineRules.kt`.
- The database is at version 5. Every migration from version 1 is kept and tested, and
  destructive fallback is off.

## Tech stack

| Category | Technology |
|----------|------------|
| Language | [Kotlin](https://kotlinlang.org/) |
| UI | [Jetpack Compose](https://developer.android.com/develop/ui/compose), [Material 3](https://m3.material.io/) |
| Dependency injection | [Hilt](https://dagger.dev/hilt/) |
| Database | [Room](https://developer.android.com/training/data-storage/room) |
| Background work | [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) |
| Image loading | [Coil](https://coil-kt.github.io/coil/) |
| Zoomable viewer | [Zoomable](https://github.com/usuiat/Zoomable) |
| Drag to reorder | [Reorderable](https://github.com/Calvin-LL/Reorderable) |

Only open-source libraries are used.

---

## Credits and licence

Paperize is created by [Anthony La](https://github.com/Anthonyy232); if you find it useful,
consider [supporting him](https://github.com/sponsors/Anthonyy232). The Simplified Chinese
translation started with Paperize's contributors.

Licensed under the **GNU General Public License v3.0**; see [LICENSE](LICENSE).
