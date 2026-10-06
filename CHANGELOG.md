## Unreleased (fork)

### Development setup
- Debug builds install as "Paperize Debug" (`com.anthonyla.paperize.debug`) next to the release app, with their own live wallpaper name and a working "Change wallpaper" shortcut.
- Room database schemas are tracked in git so migrations can be tested against them.
- CI runs tests and lint on every pull request and push to `master`, and skips the signed build and release when signing secrets are not configured.

### Library and albums
- The album screen has a Reorder action again, opening the drag-to-reorder screen. Albums and folders open in the new "Rotation order" view by default, which shows images in the order they change in.
- New images keep their place: directly added images stay before folders, and new files found in a folder go to the end of that folder rather than the end of the album.
- SVG images now work as static and live wallpapers. TIFF is no longer listed as supported, because Android can't decode it; imports skip files in unsupported formats and say how many were skipped.
- Removing images, folders or albums gives back Android's file permissions that nothing else uses. Imports warn as the app nears Android's limit of 512 kept permissions and stop before it, suggesting a folder instead.
- Images Paperize can no longer read are marked in the album, with a banner to grant access again or remove them. They are skipped in rotation, and an album whose images all lost access stays selected instead of being treated as empty.
- The database moves to version 5, adding everything later planned features need in one migration.

### Wallpaper reliability
- The live wallpaper shows the same image again after a reboot, app update or re-applying it, instead of moving to the next one. The current live image appears in the "Current wallpapers" card.
- The live wallpaper tries up to 10 queued images when one can't be decoded, like static mode.
- Live wallpaper previews no longer use up images, and one change event advances the wallpaper once, however many wallpaper engines are running.
- Scheduled changes now show the same "album empty" and "couldn't change" notifications as manual ones, and stop their background job once changing has been turned off.

## v4.2.0

### Library and albums
- Make image imports and folder refreshes cancellable and transactional, with accurate progress and useful failure feedback.
- Preserve library entries when a document provider is temporarily unavailable, avoid duplicate imports, and refresh folder metadata and covers consistently.
- Preserve library data when upgrading older database versions.
- Improve album deletion, selection cleanup, sorting, and empty-album actions.

### Wallpaper reliability
- Keep home and lock schedules consistent across restarts, manual changes, and settings edits.
- Prevent rapid settings edits from overwriting newer album selections or pause/resume actions.
- Share wallpaper application and rendering between manual changes and scheduled jobs, with consistent failure recovery.
- Correct static image sizing, thin-image decoding, and FIT/STRETCH fallback rendering.

### Live wallpaper and interface
- Fix crossfade completion and preserve pending wallpaper changes when folding, resizing, or recreating the rendering surface.
- Improve OpenGL resource cleanup, blur rendering, and memory use.
- Respect animation settings in previews and improve sorting accessibility and settings feedback.
- Remove redundant UI, unused dependencies, obsolete compatibility code, and low-value tests and comments.

## v4.1.1

- Fixed manual changes triggering an immediate extra automatic change by deferring
  the next run for the full interval and preserving it across settings updates.
- Added Simplified Chinese translations for setup, settings, wallpaper controls,
  notifications, and accessibility labels, with Android app-language support.
- Corrected the privacy notice to explain that Paperize displays its own
  notifications and does not read notifications from other apps.

## New Contributors

* @zxiaoshen made their first contribution in https://github.com/Anthonyy232/Paperize/pull/605

**Full Changelog**: https://github.com/Anthonyy232/Paperize/compare/v4.1.0...v4.1.1

## v4.1.0

- Added a Set wallpaper action to image previews, with separate home, lock, and
  combined home-and-lock targets that preserve each screen's scaling and effects.
- Reset the affected automatic countdown after a successful manual change from
  Paperize, its launcher shortcut, or its Quick Settings tile.
- Added live-wallpaper intervals as short as one minute while the wallpaper is
  visible, with lifecycle-aware pausing and timer resets after manual changes.
- Replaced the undersized status-bar asset with a dedicated notification icon.
- Updated Paperize for the Android 17 SDK and refreshed Kotlin, Gradle, Compose,
  AndroidX, Material, Coil, Zoomable, and GitHub Actions dependencies.
- Expanded emulator coverage for Android wallpaper colors, every static and live
  visual effect, OpenGL shaders, scheduling policy, and foldable display sizing.

**Full Changelog**: https://github.com/Anthonyy232/Paperize/compare/v4.0.3...v4.1.0

## v4.0.3

- Kept scheduled static changes in the device's natural orientation, even when a
  landscape game or app is in the foreground.
- Rendered FIT, FILL, STRETCH, and NONE against one physical display panel to fix
  black canvases, excessive stretching, zoom, and inconsistent home/lock alignment.
- Restored centered static FILL behavior by default and added an explicit horizontal
  scrolling option for users who want wide wallpapers to move across home pages.
- Added a persistent pause/resume control that keeps selected albums intact, while
  leaving manual static and live wallpaper changes available when paused.
- Removed the unnecessary network-state permission and unused network image loader.
- Updated Hilt and the coroutine test library and expanded rotation, scaling,
  scrolling, pause/resume, static-effect, live-renderer, and scheduler verification.

**Full Changelog**: https://github.com/Anthonyy232/Paperize/compare/v4.0.2...v4.0.3

## v4.0.2

- Reloaded the current live wallpaper at native resolution after fold, unfold,
  surface-size, and scaling changes without advancing the wallpaper queue.
- Made adaptive brightness update the wallpaper already on screen and removed
  the brightness dip that could occur halfway through live crossfades.
- Kept live vignette shading continuous across large images split into multiple
  GPU texture tiles.
- Prevented a delayed effect-slider save from overwriting a switch or other
  setting changed immediately afterward.
- Restored the launcher app shortcut for gesture apps and other launchers, with
  automatic routing to the configured static or live wallpaper engine.
- Added the missing daily album refresh to live schedules and hardened boot
  recovery so valid jobs are restored without duplicates and stale jobs are removed.
- Removed two unused legacy serialization and document-file dependencies from
  the release package.
- Expanded device coverage for every static effect and verified synchronized,
  independent, manual, live, and reboot scheduling paths.

**Full Changelog**: https://github.com/Anthonyy232/Paperize/compare/v4.0.1...v4.0.2

## v4.0.1

- Restored native static FILL scrolling so wide wallpapers move between their real
  left and right edges without synthetic launcher-sized overflow.
- Added independent brightness, blur, vignette, and grayscale controls for home
  and lock screens, including when both screens share one album and schedule.
- Refreshed folder-backed albums whenever Paperize returns to the foreground so
  added and removed files appear without restarting the app.
- Improved foldable sizing on Android 17 by including inactive built-in panels
  while excluding external displays.
- Made wallpaper changes commit queue and current-wallpaper state only after
  Android accepts the bitmap, with rejected changes restored for retry.
- Corrected live wallpaper parallax enablement, intensity, edge traversal, and
  launcher-offset clamping, and clarified that it responds to home-page swipes.
- Added device regressions for EXIF rotation, static FILL overflow, and folded
  display sizing, plus focused queue and live-renderer unit coverage.

**Full Changelog**: https://github.com/Anthonyy232/Paperize/compare/v4.0.0...v4.0.1

## v4.0.0

- Completely rewrote Paperize for Android 12 and newer with a modern Compose interface.
- Added static and live wallpaper modes with independent home and lock screen settings.
- Added scaling, blur, darken, vignette, grayscale, adaptive brightness, parallax,
  double-tap, shuffle, interval, and manual wallpaper controls.
- Added current-wallpaper previews, Quick Settings support, and progress indicators
  for large wallpaper and folder imports.
- Improved folder scanning, queue refreshes, bitmap memory usage, scheduling, and
  wallpaper rendering reliability.
- Fixed home and lock screen synchronization, foldable display sizing, EXIF rotation,
  and FIT, FILL, STRETCH, and NONE scaling behavior.
- Removed the obsolete all-files storage permission in favor of Android's scoped
  document access.
- Updated dependencies and repaired the release workflow for signed, versioned APKs.

**Upgrade note:** Updating from Paperize 3 resets local albums and settings once
because Paperize 4 uses a new storage model. Users of the 4.0.0 alpha are not reset
again.

## New Contributors

* @gpunto made their first contribution in https://github.com/Anthonyy232/Paperize/pull/415

**Full Changelog**: https://github.com/Anthonyy232/Paperize/compare/v3.2.1...v4.0.0
