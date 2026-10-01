# MotionCraft (Live Photos Studio)

<p align="center">
  <img src="Screenshot/logo.svg" width="100" alt="MotionCraft Logo" />
</p>

<p align="center">
  <b>Android Live Photos / Motion Photos viewer, converter, synthesizer, and batch manager</b>
</p>

<p align="center">
  <a href="README_zh.md">简体中文</a> | <b>English</b>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License"></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.2.10-blue.svg?logo=kotlin" alt="Kotlin"></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose" alt="Jetpack Compose"></a>
  <a href="https://www.android.com/"><img src="https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg?logo=android" alt="Platform"></a>
  <a href="CHANGELOG.md"><img src="https://img.shields.io/badge/Version-1.2.2-orange.svg" alt="Version"></a>
</p>

---

## Project Overview

**MotionCraft** is a lightweight, dedicated Live Photos and Motion Photos toolkit built for Android.
The application allows you to scan and play embedded motion photos, extract video streams and cover images, convert standard videos into live photos, and synthesize standalone pictures with video clips.

---

## Format and Platform Support

| Platform / Format | Status |
| :--- | :---: |
| **Google Motion Photo** | Supported |
| **Xiaomi Live Photo** | Supported |
| **OPPO / OnePlus Live Photo** | Supported |
| **TikTok / Douyin Live Photo Sharing** | Supported |
| **Apple Live Photo** | In Development |
| **vivo / iQOO Live Photo** | In Development |

> Note: Live photo implementations differ across manufacturers. While most adhere to the "static image + embedded video" layout, internal differences exist in XMP/EXIF tags, MP4 offsets, and proprietary vendor containers.
>
> Platform status reflects the current compatibility verified within MotionCraft.

---

## Core Features

- **Live Photo Gallery Management**
  - Automatically scans local storage for motion photos containing embedded micro-videos (`MicroVideoOffset`).
  - Smooth grid view with multi-selection and batch deletion.
- **Motion Photo Playback**
  - Long-press any photo card to play dynamic micro-videos with gesture interaction.
- **Video and Live Photo Conversion**
  - Convert standard videos into Android Motion Photos (JPEG + MP4).
  - Extract standalone MP4 video clips and JPEG cover images from live photos.
- **Image and Video Pairing**
  - Pair separate images and short videos, write XMP metadata, and synthesize them into standard live photos.
- **High-Definition Frame Extraction**
  - Frame-by-frame seeking to capture and export crisp static images from live photos.
- **XMP Metadata Inspector**
  - Inspect embedded metadata parameters including `GCamera:MicroVideo` and `MicroVideoOffset`.
- **Custom Themes and Multi-Language**
  - Material You dynamic color theming with light/dark modes and accent color switching.
  - Built-in multi-language switching for Simplified Chinese, Traditional Chinese, English, and Japanese.
- **Diagnostics and Logs**
  - Built-in runtime interaction logs with debug overlay and one-click copy/export.

---

## App Screenshots

| Gallery | Video to Live | Dual Pairing | Settings |
| :---: | :---: | :---: | :---: |
| <img src="Screenshot/01_gallery.png" width="220" alt="Gallery" /> | <img src="Screenshot/02_convert.png" width="220" alt="Video to Live" /> | <img src="Screenshot/03_pairing.png" width="220" alt="Dual Pairing" /> | <img src="Screenshot/04_settings.png" width="220" alt="Settings" /> |
| Local motion photo discovery | Cover extraction & synthesis | Manual picture & video merge | App preferences & themes |

---

## Technical Principles

The Android Motion Photo format encapsulates JPEG image data and MP4 video data inside a single file:

```
+--------------------------------+----------------------------+
|  JPEG Image Data               |  Embedded MP4 Video Data   |
|  (Contains XMP App1 Segment)   |  (At the end of file)      |
+--------------------------------+----------------------------+
  ^                              ^
  |                              |
  +-- MicroVideoOffset Specifies -+
```

1. **XMP Offset Parsing**: Reads the JPEG header (`0xFFE1` APP1 Marker) and parses `GCamera:MicroVideoOffset` to locate the starting byte of the trailing MP4 video stream.
2. **Video Extraction**: Uses `RandomAccessFile` to seek and read the trailing MP4 data based on the calculated offset.
3. **Playback Control**: Integrates Media3 ExoPlayer with Jetpack Compose views for gesture-triggered playback.

---

## Project Structure

```
MotionCraft/
├── .github/                    # CI/CD workflows and issue templates
│   ├── ISSUE_TEMPLATE/         # Bug report and feature request templates
│   └── workflows/              # GitHub Actions release and verification workflows
├── Screenshot/                 # App preview screenshots and app icon
├── app/                        # Main Android application module
│   ├── build.gradle.kts        # Module build configuration (64-bit ABI filter & R8)
│   ├── proguard-rules.pro      # ProGuard / R8 optimization rules
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/example/
│       │   │   ├── MainActivity.kt
│       │   │   ├── core/       # Core conversion engine and media protocols
│       │   │   │   ├── converter/     # Motion format converters (LivePhotoToolConverter)
│       │   │   │   ├── media/         # Image processing, video transcoding & frame cache
│       │   │   │   └── protocol/      # Vendor live photo protocol definitions & packer
│       │   │   ├── data/       # Room database and local persistence (AppDatabase)
│       │   │   ├── ui/         # Jetpack Compose UI
│       │   │   │   ├── LivePhotoApp.kt# Main navigation container
│       │   │   │   ├── components/    # Player, cards, wavy sliders and drawer components
│       │   │   │   ├── screens/       # Gallery, Convert, Pair, Details, Diagnostics & Settings
│       │   │   │   └── theme/         # Material 3 dynamic color scheme and typography
│       │   │   ├── util/       # XMP detector, parser and logger
│       │   │   └── viewmodel/  # Architecture ViewModels (LivePhotoViewModel)
│       │   └── res/            # App icons, localized strings (zh/en/ja) and styles
│       └── test/               # Local JVM and Robolectric unit tests
├── gradle/
│   └── libs.versions.toml      # Gradle Version Catalog
├── build.gradle.kts            # Root build script
├── settings.gradle.kts         # Project settings
├── gradle.properties           # Gradle environment configuration
├── CHANGELOG.md                # Release notes and changelog
├── CONTRIBUTING.md             # Contribution guidelines
├── SECURITY.md                 # Security policy
├── LICENSE                     # Open-source license (Apache 2.0)
├── README_zh.md                # Simplified Chinese documentation
└── README.md                   # English documentation
```

---

## Build Instructions

Build the Release APK using Gradle:

```bash
./gradlew assembleRelease
```

Build output directory: `app/build/outputs/apk/release/`

---

## System Requirements and Permissions

### System Requirements
- **OS Version**: Android 8.0 (API Level 26) or higher
- **Architecture**: `arm64-v8a` / `x86_64` (64-bit only)

### Permissions
- `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO`: Read local live photos and videos on Android 13+
- `READ_EXTERNAL_STORAGE`: Read local media files on Android 12 and below
- `WRITE_EXTERNAL_STORAGE`: Save generated live photos to storage on Android 9 and below

---

## License

This project is open-sourced under the [Apache 2.0 License](LICENSE).
