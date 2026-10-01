# MotionCraft (Live Photos Studio)

<p align="center">
  <img src="Screenshot/logo.svg" width="100" alt="MotionCraft Logo" />
</p>

<p align="center">
  <b>Android Live Photos / Motion Photos Viewer, Converter, Merger & Manager</b>
</p>

<p align="center">
  <a href="README_zh.md"><b>简体中文说明文档 (Chinese Version)</b></a>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License"></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-1.9.0-blue.svg?logo=kotlin" alt="Kotlin"></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose" alt="Jetpack Compose"></a>
  <a href="https://www.android.com/"><img src="https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg?logo=android" alt="Platform"></a>
  <a href="CHANGELOG.md"><img src="https://img.shields.io/badge/Version-1.2.2-orange.svg" alt="Version"></a>
</p>

---

## Introduction

**MotionCraft** is an Android utility designed for viewing, converting, synthesizing, and managing Motion Photos and Live Photos.
It provides local motion photo scanning and playback, cover/video extraction, video-to-live-photo conversion, and manual image-video pairing.

---

## Platform & Format Support

| Platform / Format | Status |
| :--- | :---: |
| **Google Motion Photo** | Supported |
| **Xiaomi Live Photo** | Supported |
| **OPPO / OnePlus Live Photo** | Supported |
| **TikTok / Douyin Live Photo Share** | Supported |
| **Apple Live Photo** | In Development |
| **vivo / iQOO Live Photo** | In Development |

> Different manufacturers implement Live Photos differently. Even though they share the "static image + dynamic video" structure, differences exist in XMP/EXIF tags, MP4 embedding layout, and private metadata.
>
> Platform status reflects actual compatibility implemented in MotionCraft.

---

## Key Features

- **Live Photo Gallery**
  - Automatically scans local albums for Motion Photos containing embedded micro-video (`MicroVideoOffset`).
  - Smooth grid view with multi-select and batch delete support.
- **Motion Photo Playback**
  - Long-press any card to play embedded micro-videos with full-screen gesture controls.
- **Video & Live Photo Converter**
  - Convert standard videos into Android Motion Photos (JPEG + MP4).
  - Extract standalone MP4 videos and JPEG covers from Live Photos.
- **Image & Video Pairing**
  - Pick any standalone image and short video clip, write XMP metadata, and combine them into a Live Photo.
- **High-Resolution Frame Capture**
  - Scrub frame by frame to extract and save clear still images from Live Photos.
- **XMP Metadata Inspector**
  - Inspect parameters like `GCamera:MicroVideo` and `MicroVideoOffset`.
- **Theming & Multilingual Support**
  - Material You dynamic color theming with dark and light mode support.
  - Built-in language switching across Simplified Chinese, Traditional Chinese, English, and Japanese.
- **Diagnostics & Logging**
  - Built-in runtime interaction logging with floating debug window and log sharing support.

---

## App Screenshots

| Gallery | Video to Live | Dual Pairing | Settings |
| :---: | :---: | :---: | :---: |
| <img src="Screenshot/01_gallery.png" width="220" alt="Gallery" /> | <img src="Screenshot/02_convert.png" width="220" alt="Convert" /> | <img src="Screenshot/03_pairing.png" width="220" alt="Pairing" /> | <img src="Screenshot/04_settings.png" width="220" alt="Settings" /> |
| Scan & Play Local Photos | Extract Cover & Convert | Manual Image/Video Merge | Theme & Preferences |

---

## How It Works

Android Motion Photo format stores the JPEG cover image and MP4 video data inside a single file:

```
+--------------------------------+----------------------------+
|  JPEG Image Data               |  Embedded MP4 Video Data   |
|  (Contains XMP App1 Segment)   |  (At the end of file)      |
+--------------------------------+----------------------------+
  ^                              ^
  |                              |
  +-- MicroVideoOffset Specifies -+
```

1. **XMP Offset Parsing**: Reads JPEG header (`0xFFE1` APP1 Marker) and parses `GCamera:MicroVideoOffset` to locate the starting byte of the trailing MP4 stream.
2. **Video Extraction**: Uses `RandomAccessFile` to seek and read the trailing MP4 data by byte offset.
3. **Playback Control**: Integrates Media3 ExoPlayer with Jetpack Compose views for gesture-triggered playback.

---

## Project Structure

```
MotionCraft/
├── app/                        # Main Android application module
│   ├── build.gradle.kts        # Module build configuration (64-bit ABI filter, R8 minify)
│   ├── proguard-rules.pro      # ProGuard / R8 optimization rules
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/example/
│       │   │   ├── MainActivity.kt
│       │   │   ├── core/       # Conversion engine, media processing & protocols
│       │   │   │   ├── converter/     # Format converter logic
│       │   │   │   ├── media/         # Image processing, video transcode & frame cache
│       │   │   │   └── protocol/      # Multi-brand protocol definitions & packing
│       │   │   ├── data/       # Room Database & local persistence
│       │   │   ├── ui/         # Jetpack Compose UI & components
│       │   │   │   ├── LivePhotoApp.kt# App scaffold & navigation
│       │   │   │   ├── components/    # Player, cards, wavy slider & bottom sheets
│       │   │   │   ├── screens/       # Gallery, convert, pair, diagnostics & settings
│       │   │   │   └── theme/         # Material 3 dynamic color & typography
│       │   │   ├── util/       # XMP helpers & debug log manager
│       │   │   └── viewmodel/  # Global state management ViewModel
│       │   └── res/            # Drawables, strings (zh/en/ja) & theme resources
│       └── test/               # Unit and Robolectric tests
├── .github/                    # CI/CD workflows & issue templates
│   ├── ISSUE_TEMPLATE/         # Bug report & feature request templates
│   └── workflows/              # Automated release & CI workflows
├── Screenshot/                 # App preview screenshots & logo
├── CONTRIBUTING.md             # Contribution guidelines
├── CHANGELOG.md                # Release notes & changelog
├── SECURITY.md                 # Security policy
├── LICENSE                     # Apache 2.0 License
├── README_zh.md                # Simplified Chinese documentation
└── README.md                   # English documentation
```

---

## Build Instructions

Build Release APK using Gradle:

```bash
./gradlew assembleRelease
```

Output directory: `app/build/outputs/apk/release/`

---

## Requirements & Permissions

### Requirements
- **OS Version**: Android 8.0 (API Level 26) or higher
- **Architecture**: `arm64-v8a` / `x86_64` (64-bit only)

### Permissions
- `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO`: Read local live photos & videos on Android 13+
- `READ_EXTERNAL_STORAGE`: Read media files on Android 12 and below
- `WRITE_EXTERNAL_STORAGE`: Save generated Live Photos on Android 9 and below

---

## License

This project is licensed under the [Apache 2.0 License](LICENSE).
