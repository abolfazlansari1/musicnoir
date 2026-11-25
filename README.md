# Noir Music

A native Android local-music-player project built with Kotlin, Jetpack Compose and AndroidX Media3.

## Included in this source package

- Dark black/purple Compose interface and dedicated launcher icon resources
- Local audio-library scan through MediaStore (Android 8.0+)
- Album artwork loading from Android's album-art content provider
- Search across title, artist and album
- Home, library, favorites, equalizer and settings sections
- Favorites and playlist definitions persisted locally
- Media3 playback service for background playback and media-session integration
- Mini player and expanded player controls
- Queue playback, previous/next, seek, shuffle, repeat and playback speed
- LRCLIB online lyrics lookup (internet required)
- Sleep timer
- Android audio effects integration (equalizer, bass boost and virtualizer; device-dependent)
- GitHub Actions workflow to build and upload a debug APK

## Requirements

- Android Studio with Android Gradle Plugin 8.7.3 support
- JDK 17
- Android SDK 35
- Minimum Android version: 8.0 (API 26)

## Build

Open this folder in Android Studio, allow Gradle sync, then run the `app` configuration on a device.

Or use GitHub Actions: push the project to a repository's `main` or `master` branch. The workflow in `.github/workflows/android.yml` runs `assembleDebug` and uploads `Noir-Music-debug` as an artifact.

## Important implementation notes

This is an upgraded development source package, not a certified or fully device-tested release. A successful GitHub Actions build and real-device testing are still required before treating it as stable.

The current implementation does **not yet include** permanent embedded audio-tag editing (title/artist/album tags), SAF-based file deletion/rename flows, a full folder browser, local LRC file import, or backup/restore. These require additional Android storage-permission and file-format handling and must not be confused with editing only the app's display state. Audio effects also depend on support from the device's Android audio-effect engine.
