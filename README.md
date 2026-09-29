# GliphLY (Nothing Phone 3a / 3a Pro)

## Setup
1. Open this folder in Android Studio and sync — the Glyph SDK AAR is fetched
   automatically into `app/libs/` on first sync (see `fetchGlyphSdk` in
   `app/build.gradle.kts`). No manual download needed.
2. Enable debug mode (re-run every 48h):
   `adb shell settings put global nt_glyph_interface_debug_enable 1`
3. Run on the phone. Tap zones to light them; "Add step" records the current
   pattern; "Play loop" replays. Keep the app in the foreground — the SDK
   only works for foreground apps.

## Auto-update
`UpdateChecker.kt` checks a GitHub repo's latest Release for a newer version
than what's installed, and if found, downloads the APK and lets Android's
installer prompt take it from there. Android requires that tap-to-confirm —
no app can silently reinstall itself, sideloaded or not.
To use it:
1. Push this project to a GitHub repo of your own.
2. Set `REPO = "yourusername/GlyphLab"` in `UpdateChecker.kt`.
3. Tag releases (e.g. `v0.2`) and attach the built APK as a release asset.
4. Tap "Check for updates" in the app.
