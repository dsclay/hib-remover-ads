# AdFree Browser (Android)

Personal-use WebView browser that reuses this repo's ad-blocking scripts and rules.
Licensed GPL-3.0 like the rest of the repo.

## How it maps to the extension

| Extension | App |
|---|---|
| `declarativeNetRequest` (`src/rules/ads.json`) | `AdBlocker` checks every sub-resource in `shouldInterceptRequest` |
| `src/content/generic.css` | injected at document start on every page (`WebScripts.cosmetic`) |
| `src/content/yt-main.js` | injected at document start on `*.youtube.com` (strips ad data from player responses) |
| `src/content/youtube.js` | same, fallback that mutes and skips ads that still play |

`app/build.gradle.kts` copies those files from `../src` into `app/src/main/assets/web/` before every build, so merging upstream changes updates the app.

## Extras

- Share a link to the app, or tap a YouTube link and choose it, to play it directly.
- "Paste" button in the address bar: paste a link and go.
- Fullscreen video, back navigation, blocked-request counter.

## Build

CI: `.github/workflows/android.yml` builds a debug APK on every push (download it from the run's Artifacts).

Local: open `android/` in Android Studio, or run `gradle assembleDebug` with Gradle 8.9 and JDK 17.

## Not done yet (next steps)

- Only `ads.json` is used (~130 ad domains plus a few URL rules). `block.json` / `allow.json` need a converter to a compact format.
- Per-site cosmetic rules (`cosmetic.json`) and scriptlets.
- Background audio, pause-per-site, settings screen.
- YouTube changes its ad delivery often; if ads return, update `yt-main.js` in the extension and rebuild.
