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

### Scheduling and settings
- Turning the Home or Lock screen off keeps its album, ready for when it's turned on again. The change button, Quick Settings tile, shortcut and effects leave a turned-off screen alone, and a screen still waiting for its album no longer stops the other screen changing.
- Turning a screen on, picking its album or turning changing on shows the first image straight away and starts that screen's countdown from then, so nothing changes twice. Turning one screen on or off, or switching separate schedules, keeps the other screen's countdown.
- Home and Lock using the same album now show different images: each screen has its own shuffle order, neither shows the image the other is showing, and in order mode the lock screen starts half-way through the album.
- Effects and scaling can be changed while changing is paused: the current image is re-rendered and never swapped for another. The effect controls are greyed out while neither screen is turned on.
- Rotation progress survives new images: imports and folder refreshes add them to the current round instead of starting it again.
- A folder's Refresh also removes files deleted from the folder. Background refreshes compare each folder with one scan of it instead of checking every image separately, and opening the app refreshes at most every 4 hours (the daily 3 AM refresh is unchanged).
- Settings are no longer lost: slider changes are saved as soon as you let go (the wallpaper re-renders shortly after, or straight away when you leave the screen), and interval boxes keep what you type until you press Done or leave them.
- In live mode, Paperize no longer clears your album when it isn't the live wallpaper on opening the app. A banner offers "Set live wallpaper" instead, and the check also looks at the lock screen on Android 14+. On Xiaomi phones it explains the "change wallpaper" permission HyperOS asks for.
- Finishing onboarding no longer rebuilds the app's navigation.
- Double-tapping the live wallpaper or changing it on screen-off restarts the background countdown, like the tile and shortcut.

### Interface and accessibility
- The image viewer is readable in dark theme: it always shows the image on black with light controls, and its back button has a full-size touch area.
- Dark mode is now a System / Light / Dark choice.
- "Change wallpaper now" and "Set wallpaper" show that they are working and then say what happened. Problems get their own notification channel, which makes a sound by default.
- The Quick Settings tile has a proper icon and the label "Next wallpaper", shows whether automatic changing is on or paused, and is greyed out until an album is chosen.
- Folders in an album show their cover and image count, and album cards show how many wallpapers they hold. Screen readers no longer hear names twice, and the Home and Lock cards are announced as switches.
- Setting descriptions no longer disappear when a switch is turned on. Horizontal scrolling is offered only where it works: the home screen with Fill.
- The splash screen stays until settings have loaded, so the app opens in the right theme without a blank frame.
- Imports keep running if you leave the album; hide the progress dialog to keep browsing.

### Wallpaper rendering
- The live wallpaper's vignette no longer changes when blur is switched on, and live blur and vignette now match the static wallpaper at the same strength (live blur is gentler than before).
- If the GPU can't apply effects, blur now really happens on the CPU instead of being skipped.
- Horizontal scrolling keeps at most three screens' width of a wide image, so very large panoramas no longer run out of memory.
- Screen size detection ignores casting and other virtual displays on Android 12–16.
- Wallpaper requests from the app, tile or shortcut that Android won't run in the foreground now run as a background job instead of crashing.

### Development setup
- Test fixtures shared by unit and device tests live in `app/src/sharedTest`; unused code and the JitPack repository were removed.

### Widgets
- Three home-screen widgets: Shuffle Home, Shuffle Lock and Shuffle Both. Each is a one-cell button that can be widened to two cells to show its name, uses your wallpaper's Material You colours in light and dark, and has a preview in the widget picker.
- A tap shows the next image for that screen and restarts its countdown, like the tile and shortcut. In live mode all three change the live wallpaper.
- A widget whose screen isn't set up is greyed out, and a tap says what to set up. Shuffle Lock confirms its tap with a short message, since the lock screen can't be seen from the home screen.

### Live wallpaper
- New "Auto-Pan Cut-Off Images" setting: when Fill or None cuts part of an image off, it slowly moves up and down (tall images) or side to side (wide images), easing at each end, and starts again at the top or left with each new image. A speed slider runs from 5 minutes to 10 seconds per sweep (default: 1 minute).
- Auto-pan draws frames only while the wallpaper is visible, at most about 30 a second and far fewer for slow or short pans. On wide images it takes the place of parallax.
- Very long panoramas are decoded at a capped size in live mode, so they no longer run out of memory and get skipped.

### Album settings, favourites and exclusions
- Albums have a menu (⋮) with "Album settings" and "Delete album". Album settings holds the album's name, what its favourites do, and its own effects; every change is saved straight away.
- Albums can be renamed. Names stay unique regardless of upper and lower case, and a renamed album keeps its place in the Library. Creating an album uses the same rule.
- Images can be marked as favourites or excluded from rotation, including images inside folders: long-press to select (now also in folder views), then use the heart or the exclusion toggle. Selecting a folder marks all of its images. Marks survive folder refreshes.
- Excluded images are dimmed, marked and never come up; one excluded while on screen stays until the next change. If every image in an album is excluded, changing says so instead of reporting the album as unreadable.
- "Favourites" and "Excluded" filters above the grid list every matching image in the album, folders included. Folder tiles say how many of their images are excluded.
- Each album chooses what its favourites do: Marker only (the default), Show more often (twice as often while Shuffle is on), or Favourites only (falls back to every image when there are none). Marks and modes apply from the next change, without starting the round again; changing the mode starts a new round.
- "Custom effects for this album" gives an album its own brightness, blur, vignette and grey filter, used on every screen it is shown on, static or live. The Wallpaper tab says when an album's own effects replace its settings, and editing those settings no longer re-sets that album's wallpaper.
- Home and Lock now have separate scaling choices when both are turned on.

### Smarter scheduling
- A "Schedule" choice on the Wallpaper tab: change every interval as before, or at set times of day (for example 07:00 and 19:00). Set times use battery-friendly alarms that need no special permission, so a change can come up to about 10 minutes after its time, or when the phone is next used if it was left asleep. Times are added and edited with a clock or by typing.
- "More Scheduling Options" opens a screen with the new options and the card says which are on.
- Night albums: each screen (or the live wallpaper) can use a different album at night. Night either runs between two clock times or follows the phone's dark theme. At the switch, screens with a night album change to it, and the next change after that always comes from the album in use.
- Static mode can also change when the screen turns off and/or when the phone is unlocked, choosing which screens each changes and a minimum gap, so a screen that changed recently is left alone. Android requires a notification while Paperize listens for these. It is silent, but Android may still show its icon in the status bar, so the options screen has a one-tap way to turn it off; the changes keep working without it.
- "Only change while charging" and "Pause in battery saver" hold back automatic changes (intervals, set times, the day/night switch, screen off and unlock, and the live wallpaper's short timer and screen-off change). Changing by hand with the button, tile, widgets, shortcut or double-tap always works. With "Only while charging", a change that falls due while unplugged happens once the phone is charging.
- Static wallpapers with adaptive brightness are redrawn when the dark theme switches: within seconds when it is switched by hand, within 15 minutes when a schedule switches it, and at once while Paperize is open.
- Set times and alarms are put back after a restart, an app update or a time-zone change, and a set time missed while the phone was off is caught up once.

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
