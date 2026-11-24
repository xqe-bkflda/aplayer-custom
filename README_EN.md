# aplayer-custom

> An APlayer build that can actually play NetEase Cloud Music.

A personal fork of [rRemix/APlayer](https://github.com/rRemix/APlayer) 2.1.2.1. The upstream project is a great local player with WebDAV support — this branch adds online music and fixes a pile of playback-page and lyrics bugs.

**Current version: `v2.1.2.1-custom`** · [Download the APK](https://github.com/xqe-bkflda/aplayer-custom/releases/latest)

## What changed

### Online music

| Feature | Notes |
|:-|:-|
| NetEase Cloud Music, end to end | search (track / playlist / suggestions), playlists, leaderboards, daily radar, URL resolution, download with tags, lyrics |
| Three login methods | SMS code, phone + password, QR code |
| Kugou source | search and streaming, used when NetEase has no licence for a track |
| Real collect counts | the red heart on the playback page shows the actual number of saves |
| Comments | in-app comment sheet with paging |
| Batch download | long-press on the "songs" tab or a playlist to multi-select and download a whole page |

### Song recognition

Play a track out loud and the app identifies it and adds it straight to the online library.

- Acoustic fingerprinting via `afp.wasm`, matched against NetEase's `audio/match` endpoint
- Records for 12 seconds, returns up to 3 candidates
- Desktop shortcut jumps straight in

### Playback and lyrics

- The queue is rebuilt in the playlist's original order, and the bottom of the queue shows a **Next** hint
- Desktop lyrics are throttled and text measurement is cached, so they no longer stutter after a long run
- Lyric search order: local → NetEase → Kugou → embedded → give up; you can change it
- Fixed the occasional "no lyrics yet" that never refreshed

### Settings and details

- Manual scan and download folder both use the system folder picker (SAF)
- Removing a collect can also delete the downloaded file (on by default)
- About page redone: version, change list, privacy notes, API sources, disclaimer
- Removed check-for-updates, feedback link and the dead QQ Music entries
- Database bumped to v9; the queue now remembers NetEase IDs

### Screenshots

| | | | |
|:-:|:-:|:-:|:-:|
| <img src="screenshoots/1-home.png" width="180" /> | <img src="screenshoots/3-playing-cover.png" width="180" /> | <img src="screenshoots/4-playing-lyric.png" width="180" /> | <img src="screenshoots/5-dark.png" width="180" /> |
| Home | Playing (cover) | Playing (lyrics) | Dark |

| | | | |
|:-:|:-:|:-:|:-:|
| <img src="screenshoots/6-sleep-timer.png" width="180" /> | <img src="screenshoots/7-desktop-lyric-widget.png" width="180" /> | <img src="screenshoots/8-lockscreen.png" width="180" /> |
| Sleep timer | Desktop lyric widget | Lock screen |

## Build

Open in Android Studio (Kotlin + Jetpack Compose) and build an APK. The NetEase and Kugou endpoints are private APIs and may change without notice — see [Privacy.md](Privacy.md) and [FAQs.md](FAQs.md).

## License

GPL-3.0 — see [LICENSE](LICENSE). Upstream APlayer is MIT; this fork adds GPL-licensed components, so the fork as a whole is GPL-3.0.
