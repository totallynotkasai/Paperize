# Paperize — Phased Implementation Plan

Based on the audit of upstream `master` at `1810f8c` (v4.2.0). Your ZIP matched upstream
except for the root `build.gradle.kts`, which has been restored.

Test device: Xiaomi 2509FPN0BC, Android 16 (API 36), Nova Launcher. Paperize 4.1.1 (release
build from GitHub) is installed on it and must not be disturbed.

Sizes: **S** = small, **M** = medium, **L** = large (relative effort, not calendar time).
Status: ☐ to do · ◐ in progress · ☑ done.

---

## Ground rules

- Work happens on branches in your fork; each item group lands as its own pull request into the
  fork's `master`, which always stays buildable.
- Every phase ends with unit tests, lint and a debug build passing, a manual check on your
  phone, and a CHANGELOG entry.
- Debug builds install as **Paperize Debug** (`com.anthonyla.paperize.debug`) next to your
  4.1.1, so your real albums and settings are never touched.
- Device tests: `WallpaperUtilInstrumentedTest` sets your home-screen wallpaper to solid
  colours. It is excluded from runs on your phone unless you say otherwise.
- All database changes for this plan go into **one** migration (v4 → v5, item 1.0), so later
  phases need no further schema changes.
- Everything uses open-source AndroidX/Kotlin libraries only, which keeps F-Droid possible.

---

## Phase 0 — Foundation and safety net

| ID | Item | Size | Status |
|----|------|------|--------|
| 0.1 | Local git repo tracking upstream `master` | S | ☑ |
| 0.2 | Restore upstream root `build.gradle.kts` (was a copy of the app build file) | S | ☑ |
| 0.3 | Create fork `totallynotkasai/Paperize`, set it as `origin` (keep `upstream`), commit this plan, push `master` | S | ☑ |
| 0.3b | Add a git-ignored `local.properties` with `sdk.dir` (SDK is in `%LOCALAPPDATA%\Android\Sdk`; `ANDROID_HOME` is not set) | S | ☑ |
| 0.4 | Debug builds coexist with your install: `applicationIdSuffix ".debug"`, "Paperize Debug" label, debug override of `res/xml/shortcuts.xml` (its `targetPackage` is hard-coded) | S | ☐ |
| 0.5 | Baseline: unit tests, lint, debug build — results recorded below | S | ☑ |
| 0.6 | Track Room schemas: remove `/app/schemas` from `.gitignore`, commit `4.json` | S | ☐ |
| 0.7 | CI on the fork: skip signing/release jobs when secrets are absent; keep tests and lint on every push and PR | S | ☐ |
| 0.8 | Phone prep: Autostart and "No restrictions" battery mode for Paperize Debug; "Install via USB" in developer options | S | ☐ (you) |

**Baseline results (2026-10-06):** build succeeds in ~4 minutes. 86/86 unit tests pass. Lint
reports 0 errors and 9 warnings: dependency updates available, target SDK 36 vs 37, the
battery-optimisation request, `screenWidthDp` use in the wallpaper preview, an obsolete SDK
check, a long vector path, and a plurals candidate. Device tests have not been run yet.

**Phase 0 notes and deviations:**
- 0.3: the fork was created with only the default branch (`master`); upstream's Renovate
  branches were not copied. No git identity was configured on this machine, so the repo has a
  local one: "Andrew Worgan" with the GitHub no-reply address
  (`58782106+totallynotkasai@users.noreply.github.com`), which keeps your personal email out
  of the public history.

---

## Phase 1 — Critical fixes

| ID | Item | Audit ref | Size | Status |
|----|------|-----------|------|--------|
| 1.0 | **Schema v5 migration**, designed once for the whole plan: `wallpapers.excluded`, `wallpapers.favorite`, `wallpapers.accessLost`, `albums.effects` (nullable JSON). Includes a migration test. | — | M | ☐ |
| 1.1 | **Reconnect the reorder screen.** "Reorder" action in the album top bar opens the existing drag-to-reorder screen. Add a "Rotation order" option to the sort sheet and make it the default, so the grid shows the order wallpapers actually rotate in. | #1 | M | ☐ |
| 1.2 | **New images keep their grouping.** Direct images first, then each folder in its saved order; newly found files go to the end of *their own* folder, not the end of the album. | #14 | S | ☐ |
| 1.3 | **Unsupported formats.** Decode SVG using the SVG decoder already bundled (via Coil) for static and live. Remove TIFF from the supported list and README. Skip unsupported files at import and report how many. | #2 | M | ☐ |
| 1.4 | **File-access grants.** Release grants when images, folders or albums are deleted (when nothing else uses that file). Warn at import as individual-image grants approach Android's limit and suggest adding a folder instead. Mark images whose access was lost (`accessLost`) and show a "needs attention" banner with a re-grant action. | #3 | M | ☐ |
| 1.5 | **Live: remember the current image.** Record it after it appears on screen. On engine start (reboot, process restart, re-applying) show it again instead of advancing. Show it in the "Current wallpapers" card in live mode. | #5 | M | ☐ |
| 1.6 | **Live: skip undecodable images.** Try up to 10 queued images until one decodes, like static mode. | #7 | S | ☐ |
| 1.7 | **Live: only the real engine advances.** Preview engines never consume the queue; one receiver registration per service, not per engine. | #21 | S | ☐ |
| 1.8 | **Scheduled failures are visible.** The scheduled worker shows the same "album empty" / "couldn't change" notifications as manual changes, and cancels its own no-op jobs after auto-disabling. | #6 | S | ☐ |

**Phone check:** reboot keeps the same live image; SVG album works; reorder changes rotation;
deleting an album frees its grants (`adb shell dumpsys activity` grant list).

---

## Phase 2 — Scheduling and settings logic

| ID | Item | Audit ref | Size | Status |
|----|------|-----------|------|--------|
| 2.1 | **Turning a screen off keeps its album.** Stop clearing album IDs; the change service, reapply, tile and widgets check the Home/Lock enabled flags instead. | #9 | M | ☐ |
| 2.2 | **Enabling a second screen doesn't cancel the first one's schedule.** A new schedule created together with a manual change starts its countdown from now, so the wallpaper never changes twice. | #9 | S | ☐ |
| 2.3 | **Effects apply while paused.** Re-render the current image; never advance to a new one as a fallback while paused. Effect controls are disabled when no screen is enabled. | #10 | S | ☐ |
| 2.4 | **Different images on Home and Lock** (same album): independent shuffle orders, never apply the image currently on the other screen, and the lock screen starts offset in sequential mode. | #15 | M | ☐ |
| 2.5 | **Live album isn't wiped on app open.** Replace the silent reset with a banner and "Set live wallpaper" button; check the lock screen too (Android 14+). | #8 | S | ☐ |
| 2.6 | **Rotation progress survives new images** (merge instead of clearing queues). | #13 | S | ☐ |
| 2.7 | **Folder Refresh also removes deleted files**, by comparing against the scan. | #11 | S | ☐ |
| 2.8 | **Lighter background refresh.** Compare folder images with the scan instead of one query per image; foreground refresh at most every few hours (the 3 AM daily run stays). | #12 | M | ☐ |
| 2.9 | **Settings are never lost.** Save slider values immediately and debounce only the re-render; flush on leaving the screen. Interval boxes commit on Done or focus loss and stop resetting while you type; all interval pickers behave the same. | #18 | M | ☐ |
| 2.10 | **Onboarding** keeps a fixed start destination (no navigation rebuild when it finishes). | #20 | S | ☐ |
| 2.11 | **Live double-tap and screen-off changes** reset the background countdown, like the tile and shortcut do. | — | S | ☐ |

**Phone check:** toggle Home off/on keeps its album; Home and Lock with the same album show
different images; effects change while paused; nothing double-changes.

---

## Phase 3 — UI/UX polish, accessibility and cleanup

| ID | Item | Audit ref | Size | Status |
|----|------|-----------|------|--------|
| 3.1 | Image viewer readable in dark theme (title, back arrow, hint text) | #16 | S | ☐ |
| 3.2 | Dark mode becomes System / Light / Dark | #17 | S | ☐ |
| 3.3 | Fix Shuffle / Adaptive brightness description logic; show horizontal scrolling only when it does something (Fill + Home) | small | S | ☐ |
| 3.4 | Feedback after "Set wallpaper" / "Change now"; separate, audible channel for error notifications | small | S | ☐ |
| 3.5 | Quick Settings tile: proper monochrome icon, label, active/paused state | small | S | ☐ |
| 3.6 | Folder tiles show their cover and image count; album cards show counts; no duplicate screen-reader labels | small | S | ☐ |
| 3.7 | Live vignette looks the same with blur on or off; static and live blur/vignette strength match | #19 | M | ☐ |
| 3.8 | Splash screen stays until settings have loaded (no blank frame) | small | S | ☐ |
| 3.9 | Static rendering: real CPU blur fallback; cap panorama width with horizontal scrolling (prevents out-of-memory) | small | S | ☐ |
| 3.10 | Remove dead code: unused constants, `Uri.isValid`, "both screens" current-wallpaper lookups, `.empty()` helpers (move to test fixtures), JitPack repo. **Keep** `FOREGROUND_SERVICE_SPECIAL_USE` (needed by 6.2). | small | S | ☐ |
| 3.11 | Screen-size detection ignores casting/virtual displays on Android 12–16 | small | S | ☐ |
| 3.12 | Guard background service starts (fall back to a background job instead of crashing) | small | S | ☐ |
| 3.13 | Optional: imports keep running if you leave the album screen | — | M | ☐ |

---

## Phase 4 — Your two features

| ID | Item | Size | Status |
|----|------|------|--------|
| 4.1 | **Three home-screen widgets: Shuffle Home, Shuffle Lock, Shuffle Both.** | M | ☐ |
| 4.2 | **Live auto-pan for images that don't fit the screen.** | L | ☐ |

**4.1 Widgets.** 1×1 icon buttons, resizable to 2×1 with a label, Material You colours and
previews in the widget picker. A tap moves to the next image for that target through the same
path as the tile and shortcut, so the timer resets too. In live mode all three advance the
live wallpaper, because live mode has one album. If the target screen isn't set up, a short
message says so. Built with standard app widgets (no new library). Depends on 2.1. Tested on
Nova and the HyperOS launcher.

**4.2 Auto-pan.** A live-mode setting, "Auto-pan cut-off images", with a speed slider
(default: one full sweep per minute). Applies to Fill and None only (decision A).
- When part of the image is off-screen, it slowly pans top ↔ bottom for tall images and
  left ↔ right for wide ones. It eases back and forth and restarts at each new image.
- Runs only while the wallpaper is visible, at a capped frame rate (about 30 fps), to limit
  battery use. Parallax is turned off on the same axis while auto-pan is active.
- Static wallpapers can't animate, so the option is hidden in static mode. The existing
  horizontal-scrolling option is the closest static equivalent.
- Battery use is measured on your phone before it's finished.

---

## Phase 5 — Library features

| ID | Item | Size | Status |
|----|------|------|--------|
| 5.1 | **Rename albums** (album menu; unique-name check) | S | ☐ |
| 5.2 | **Exclude and favourite images**, including inside folders. Multi-select in album and folder views. Excluded images are dimmed and never rotate. Both survive folder refreshes. A per-album **Favourites** setting chooses Marker only / Show more often / Favourites only (decision B). | M | ☐ |
| 5.3 | **Effects per album.** "Custom effects for this album" overrides both Home and Lock effects wherever that album is shown, static and live (decision E). | M | ☐ |
| 5.5 | **Album settings sheet** (album menu) grouping Rename (5.1), Favourites mode (5.2) and Custom effects (5.3). | S | ☐ |
| 5.4 | **Separate scaling for Home and Lock** in the UI (the settings already support it) | S | ☐ |

---

## Phase 6 — Smarter scheduling

| ID | Item | Size | Status |
|----|------|------|--------|
| 6.1 | **Only change while charging / pause in battery saver.** Charging uses a background-job constraint; battery saver is checked at run time; the live short-interval timer respects both. Manual changes (button, tile, widgets) always work. | S | ☐ |
| 6.2 | **Static: change on screen off and/or unlock.** Optional listener running as a foreground service, because Android only delivers these events to running apps. Choose a target per trigger, set a minimum gap between changes, and hold the CPU awake while applying. **Hiding the notification (decision D):** Android requires one to exist, so it uses a minimum-importance channel (no status-bar icon, collapsed at the bottom of the shade). Setup includes a one-tap link to turn that channel off; the service keeps running and the notification then only appears in the system's "active apps" list. Verify this on HyperOS. | M | ☐ |
| 6.3 | **Re-apply adaptive brightness when dark mode switches** (static). Instant for manual toggles via a settings-change trigger; a light 15-minute check catches scheduled dark mode; in-app callback while running. Live mode already does this. | M | ☐ |
| 6.4 | **Fixed times of day and day/night albums.** "Change at" times (e.g. 07:00, 19:00) as an alternative to intervals; optional "Night album" per screen; the user picks whether it switches at clock times or follows the phone's dark mode (decision C). Uses battery-friendly alarms that need no special permission (accurate to a few minutes). | L | ☐ |

---

## Phase 7 — Hardening and release

| ID | Item | Size | Status |
|----|------|------|--------|
| 7.1 | Tests for the change service, scheduled worker, live engine logic, widgets and the v5 migration | M | ☐ |
| 7.2 | README (formats, features) and CHANGELOG | S | ☐ |
| 7.3 | Personal release build signed with your own key. It can't update the installed 4.1.1 (different signature), so switching means uninstalling 4.1.1 and re-creating albums, or keeping both side by side. | S | ☐ |
| 7.4 | Optional, only if you want: offer the Phase 1–3 bug fixes back to upstream as pull requests | S | ☐ |

Phases 4–6 can be reordered to taste once Phase 2 is done; 4.1 needs 2.1, 5.2 and 5.3 need 1.0.

---

## Decisions (2026-10-06)

- **Limits accepted:** auto-pan is live-only; screen-off/unlock in static mode needs a
  foreground-service notification; fixed times are accurate to a few minutes.
- **A. Auto-pan** applies when Fill (or None) cuts part of the image off; Fit never pans.
  Default speed: one full sweep per minute, adjustable.
- **B. Favourites:** the user chooses the behaviour per album — *Marker only* (filter in the
  album view), *Show more often* (weighted in shuffle), or *Favourites only* (falls back to all
  images if the album has no favourites).
- **C. Day/night albums:** the user chooses the switch trigger — clock times **or** the
  phone's dark mode.
- **D. Screen-off/unlock listener:** acceptable only if the notification isn't visible. See 6.2
  for how close Android lets us get.
- **E. Per-album effects** override both Home and Lock effects wherever that album is shown.
- **F. Distribution:** personal use only. The fork lives publicly on the user's own GitHub
  profile (that satisfies GPL-3.0 source availability). No stores, F-Droid or IzzyOnDroid.
- No shareable web page; this file is the plan of record.

---

## Audit cross-reference

| Audit item | Plan |
|---|---|
| #1 Reorder unreachable | 1.1 |
| #2 SVG/TIFF | 1.3 |
| #3 File-access grants | 1.4 |
| #4 Root build file | 0.2 ☑ |
| #5 Live current image | 1.5 |
| #6 Silent scheduled failures | 1.8 |
| #7 Live decode retry | 1.6 |
| #8 Live album wiped | 2.5 |
| #9 Screen toggle side effects | 2.1, 2.2 |
| #10 Effects while paused | 2.3 |
| #11 Folder refresh | 2.7 |
| #12 Heavy refresh | 2.8 |
| #13 Queue reset | 2.6 |
| #14 Ordering of new images | 1.2 |
| #15 Same images on both screens | 2.4 |
| #16 Viewer contrast | 3.1 |
| #17 Dark mode switch | 3.2 |
| #18 Lost settings / interval box | 2.9 |
| #19 Live vignette/blur | 3.7 |
| #20 Onboarding navigation | 2.10 |
| #21 Multiple live engines | 1.7 |
| Smaller issues and cleanup | 0.6, 0.7, 3.3–3.12 |
