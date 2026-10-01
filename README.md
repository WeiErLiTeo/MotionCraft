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

**MotionCraft** is a specialized Android utility designed for viewing, converting, synthesizing, and managing Motion Photos and Live Photos.
It provides automatic album scanning and press-to-play playback, micro-video extraction, high-resolution single frame capture, video-to-live-photo conversion, image & video pairing, and deep XMP metadata inspection.

---

## Platform & Format Support

| Platform / Format | Status | Notes |
| :--- | :---: | :--- |
| **Google Motion Photo** | Supported | Standard XMP `GCamera:MicroVideo` and `MicroVideoOffset` encapsulation |
| **Xiaomi Live Photo** | Supported | Xiaomi Gallery Live Photo scanning, cover/video extraction & synthesis |
| **OPPO / OnePlus Live Photo** | Supported | ColorOS Live Photo packaging structure recognition and conversion |
| **TikTok / Douyin Live Photo Share** | Supported | Playback and decoding of dynamic live photo shares from Douyin |
| **Apple Live Photo** | In Development | iOS HEIC/JPEG + MOV pairing compatibility |
| **vivo / iQOO Live Photo** | In Development | OriginOS Live Photo format support in progress |

---

## Key Features

- **Smart Live Photo Gallery**
  - Scans local albums for Motion Photos containing embedded micro-videos (`MicroVideoOffset`).
  - Smooth grid view with multi-select and batch deletion.
- **Press to Play & Full-Screen Playback**
  - Long-press any photo card to smoothly play embedded micro-videos with full-screen gesture controls.
- **Video to Motion Photo**
  - Select video cover frames with live preview, inject XMP metadata, and convert to standard Android Motion Photos.
- **Photo & Video Pairing**
  - Pair any standalone static photo and short video clip into a brand-compliant Live Photo.
- **Extract Video & Frame Capture**
  - Losslessly extract embedded MP4 video and JPEG covers from Live Photos.
  - Interactive sine wave slider to scrub and extract high-resolution individual still frames.
- **XMP Metadata Diagnostics**
  - Inspect byte offsets, namespace definitions, and embedded video flags.
- **Multilingual & Theming**
  - Instant in-app language switching across Simplified Chinese, Traditional Chinese, English, and Japanese.
  - Full Material Design 3 dynamic color theming (Monet), dark mode, and customizable accent palettes.
- **Developer Diagnostics Console**
  - Built-in runtime interaction logging with floating debug window and log sharing support.

---

## App Screenshots

| Gallery | Video to Live | Dual Pairing | Settings |
| :---: | :---: | :---: | :---: |
| <img src="Screenshot/01_gallery.png" width="220" alt="Gallery" /> | <img src="Screenshot/02_convert.png" width="220" alt="Convert" /> | <img src="Screenshot/03_pairing.png" width="220" alt="Pairing" /> | <img src="Screenshot/04_settings.png" width="220" alt="Settings" /> |
| Scan & Play Local Photos | Extract Cover & Convert | Manual Image/Video Merge | Theme & Preferences |

---

## How It Works

Android Motion Photo format stores the JPEG cover image and MP4 video stream inside a single file:

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
3. **Playback Control**: Integrates AndroidX Media3 (ExoPlayer) with Jetpack Compose views for seamless gesture-triggered playback.

---

## Project Structure

```text
MotionCraft/
├── app/                                    # Main Android application module
│   ├── build.gradle.kts                    # Module build configuration (64-bit ABI filter, R8 minify)
│   ├── proguard-rules.pro                  # R8 / ProGuard optimization rules
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml         # Manifest and permissions
│       │   ├── java/com/example/
│       │   │   ├── MainActivity.kt         # Main Activity with edge-to-edge layout
│       │   │   ├── core/                   # Media engine & format protocols
│       │   │   │   ├── converter/
│       │   │   │   │   └── LivePhotoToolConverter.kt # Conversion business logic
│       │   │   │   ├── media/
│       │   │   │   │   ├── ImageProcessor.kt         # Frame & image processing
│       │   │   │   │   ├── VideoProcessor.kt         # Video transcoding & trim
│       │   │   │   │   └── VideoThumbnailCache.kt    # Frame cache & thumbnails
│       │   │   │   └── protocol/
│       │   │   │       ├── LivePhotoBrandMode.kt     # Manufacturer modes (Google/Xiaomi/OPPO etc.)
│       │   │   │       └── LivePhotoProtocolPacker.kt# XMP injection & packaging
│       │   │   ├── data/
│       │   │   │   └── AppDatabase.kt      # Room Database persistence
│       │   │   ├── ui/                     # Jetpack Compose UI
│       │   │   │   ├── LivePhotoApp.kt     # App scaffold & navigation rail
│       │   │   │   ├── components/         # Modular UI components
│       │   │   │   │   ├── DebugLogSheet.kt          # Debug console bottom sheet
│       │   │   │   │   ├── LivePhotoCard.kt          # Gallery photo card
│       │   │   │   │   ├── LivePhotoPlaybackOverlay.kt# Press-to-play overlay
│       │   │   │   │   ├── SquigglyWavySlider.kt     # Sine wave scrubbing slider
│       │   │   │   │   ├── VideoPlayerComponents.kt  # Media3 player wrappers
│       │   │   │   │   └── VideoTrimSlider.kt        # Video trimming component
│       │   │   │   ├── screens/            # Application screens
│       │   │   │   │   ├── ConvertScreen.kt          # Video to Live Photo
│       │   │   │   │   ├── LibraryScreen.kt          # Motion Photo Gallery
│       │   │   │   │   ├── LivePhotoDetailScreen.kt  # Detail & frame capture
│       │   │   │   │   ├── ManualPairScreen.kt       # Dual pair merger
│       │   │   │   │   ├── SettingsScreen.kt         # Preferences & theming
│       │   │   │   │   └── XmpToolScreen.kt          # XMP metadata tool
│       │   │   │   └── theme/              # Material 3 Design System
│       │   │   │       ├── Color.kt                  # Color palette definitions
│       │   │   │       ├── Theme.kt                  # Dynamic color & themes
│       │   │   │       └── Type.kt                   # Typography styles
│       │   │   ├── util/                   # Utility helpers
│       │   │   │   ├── DebugLogManager.kt  # Interaction & runtime logger
│       │   │   │   └── MotionPhotoHelper.kt# XMP parsing & file detection
│       │   │   └── viewmodel/              # State management
│       │   │       └── LivePhotoViewModel.kt # Global UI State ViewModel
│       │   └── res/                        # Android resources
│       │       ├── drawable/               # Custom vector drawables
│       │       ├── mipmap-anydpi-v26/      # Adaptive app launcher icons
│       │       ├── values/                 # Base themes & default strings
│       │       ├── values-en/              # English strings
│       │       ├── values-ja/              # Japanese strings
│       │       ├── values-zh-rCN/          # Simplified Chinese strings
│       │       ├── values-zh-rTW/          # Traditional Chinese strings
│       │       └── xml/                    # FileProvider & backup rules
│       └── test/                           # Unit and Robolectric tests
├── .github/                                # GitHub Actions CI/CD workflows
│   ├── ISSUE_TEMPLATE/                     # Community issue templates
│   │   ├── bug_report.md                   # Bug report template
│   │   └── feature_request.md              # Feature suggestion template
│   └── workflows/                          # Workflows
│       ├── ci.yml                          # Continuous integration pipeline
│       └── release.yml                     # Automated 64-bit release build
├── gradle/
│   └── libs.versions.toml                  # Version Catalog for dependencies
├── Screenshot/                             # Screenshots and assets
│   └── logo.svg                            # App white-background vector logo
├── CONTRIBUTING.md                         # Contributing guide
├── CHANGELOG.md                            # Release changelog
├── SECURITY.md                             # Security policy
├── LICENSE                                 # Apache 2.0 License
├── build.gradle.kts                        # Root project build file
├── gradle.properties                       # Gradle JVM properties
├── settings.gradle.kts                     # Gradle settings & modules
├── README_zh.md                            # Simplified Chinese documentation
└── README.md                               # English documentation
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
- **Architecture**: `arm64-v8a` / `x86_64` (64-bit native build, ~3.4MB APK size)

### Permissions
- `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO`: Read local live photos & videos on Android 13+
- `READ_EXTERNAL_STORAGE`: Read media files on Android 12 and below
- `WRITE_EXTERNAL_STORAGE`: Save generated Live Photos on Android 9 and below

---

## License

This project is licensed under the [Apache 2.0 License](LICENSE).
