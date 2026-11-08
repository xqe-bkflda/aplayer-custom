# aplayer-custom

> An APlayer that can actually play NetEase Cloud Music.

A personal fork of [rRemix/APlayer](https://github.com/rRemix/APlayer) 2.1.2.1.
The original is a lovely local music player, but it only handles local files and WebDAV.
This fork wires in online music and fixes a pile of issues around the playing screen and lyrics.

**Current version: `v2.1.2.1-custom`** · [Download APK](https://github.com/xqe-bkflda/aplayer-custom/releases/latest)

[中文说明](README.md)

---

## What's different

### Online music

| Feature | Notes |
|:-|:-|
| NetEase Cloud Music | Search (songs / playlists / suggestions), playlists, charts, personal radar, URL resolving, tag writing on download, lyrics |
| Three login methods | SMS code, phone + password, QR code |
| KuGou source | Search + stream fetching, as a fallback when NetEase lacks the rights |
| Favorite count | Real like count under the heart button on the playing screen |
| Comments | Bottom sheet with paginated loading |
| Batch download | Long-press on the library "songs" tab or a playlist detail to enter multi-select |

### Song recognition

Play a song out loud and identify it, then push it straight into the online library.

- Extracts an audio fingerprint with `afp.wasm`, calls NetEase `audio/match`
- Records 12 seconds, returns up to 3 candidates
- Launcher long-press shortcut goes straight there

### Playback and lyrics

- Tapping a song queues the **rest of the list in its original order**, with a "next up" hint at the bottom of the queue
- Desktop lyrics are throttled and text measurement is cached — no more stutter while it sits on screen
- Lyric source order: local → NetEase → KuGou → embedded → ignore, all configurable
- Fixed the intermittent "no lyrics" that never refreshed

### Settings and details

- Manual scan and download directory both use the system folder picker (SAF)
- Un-favoriting can delete the downloaded file automatically (on by default)
- About screen rebuilt: version, changelog, privacy, API provenance, disclaimer
- Removed update check, feedback, and the dead QQ Music entry points
- Database bumped to v9 — the play queue remembers the NetEase song id

---

## Screenshots

| | | | |
|:-:|:-:|:-:|:-:|
| <img src="screenshoots/1-home.png" width="180" /> | <img src="screenshoots/3-playing-cover.png" width="180" /> | <img src="screenshoots/4-playing-lyric.png" width="180" /> | <img src="screenshoots/5-dark.png" width="180" /> |
| Home | Playing (cover) | Playing (lyrics) | Dark |

| | | |
|:-:|:-:|:-:|
| <img src="screenshoots/6-sleep-timer.png" width="180" /> | <img src="screenshoots/7-desktop-lyric-widget.png" width="180" /> | <img src="screenshoots/8-lockscreen.png" width="180" /> |
| Sleep timer | Desktop lyrics | Lockscreen |

---

## Install

Grab `1-APlayer-*-normal-release.apk` from [Releases](https://github.com/xqe-bkflda/aplayer-custom/releases).

- Requires **Android 5.0+** (minSdk 21)
- Upgrading in place migrates the database automatically; if the signature differs, uninstall first
  (that only clears the local library cache, your music files are untouched)
- A one-time notice on first launch explains what changed and where data goes

## Build

```bash
# JDK 21 + Android SDK (compileSdk 36 / NDK 28)
git clone https://github.com/xqe-bkflda/aplayer-custom.git
cd aplayer-custom

# submodules (taglib, media3)
git submodule update --init --recursive

./gradlew :app:assembleNormalRelease
```

Output lands in `app/build/outputs/apk/normal/release/`.

> Change the signing config in `app/build.gradle.kts` before building your own release —
> the bundled `Debug.jks` is only meant for test builds.

## About the APIs

Every NetEase and KuGou endpoint here was found by traffic analysis. No third-party SDK involved.

The `afp.wasm` fingerprint module and the encoding approach for song recognition are based on
[MelodyYuuka/NeteaseCloudMusic-Audio-Recognize](https://github.com/MelodyYuuka/NeteaseCloudMusic-Audio-Recognize).

**Nothing is uploaded.** Login state lives in local SharedPreferences only — no analytics,
no tracking, no crash reporting.

Full details are in-app: Settings → About → Declaration.

## Known limitations

- Some NetEase tracks require a paid membership; when no stream resolves the app says "membership may be required"
- KuGou support is search and streaming only — no login, playlists, or comments
- Song recognition relies on WebAssembly in the system WebView; very old WebViews (early Android 5) may fail
- Background recording needs the Microphone permission on recent Android versions

## Credits

Original author [rRemix](https://github.com/rRemix), plus:

[XXPermissions](https://github.com/getActivity/XXPermissions) ·
[Retrofit](https://github.com/square/retrofit) ·
[OkHttp](https://github.com/square/okhttp) ·
[Glide](https://github.com/bumptech/glide) ·
[Timber](https://github.com/JakeWharton/timber) ·
[LeakCanary](https://github.com/square/leakcanary) ·
[TinyPinyin](https://github.com/promeG/TinyPinyin) ·
[TagLib for Android](https://github.com/rRemix/taglib) ·
[ZXing](https://github.com/zxing/zxing) ·
[media3](https://github.com/androidx/media)

## License

[GPL-3.0](LICENSE), same as upstream. Fork it, change it — just keep it open.

---

<sub>A personal fork. Not affiliated with NetEase Cloud Music or KuGou.</sub>
