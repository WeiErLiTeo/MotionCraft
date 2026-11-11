# Changelog (更新日志)

All notable changes to MotionCraft will be documented in this file.

---

## [v1.3.4] - 2026-11-11 (Build 32)

### 新增与优化 (Features & Improvements)
- **苹果实况压缩包批量导入 (`.livp` / `.zip`)**：支持从苹果相册导出的压缩包中批量提取 `HEIC/JPG + MOV/MP4` 组合，自动按实况配对、独立视频、独立照片进行分类筛选，支持直接播放预览、全屏播放与一键转换存入安卓系统相册。
- **QuickTime `.MOV` 与 PCM 音频兼容修复**：
  - 针对苹果实况视频中非标准的 `qt  ` 容器及封装在 `wave` Box 内的原始 PCM 音频（`lpcm` / `sowt` / `twos` / `in24`），实现自动转封装与 AAC 编码，彻底解决安卓播放无声与导出失败问题。
  - 修复 `edts/elst` 编辑列表偏移量导致的音画截断问题。
- **视频解码器与流处理设置**：设置页新增播放解码引擎切换（自动软硬解回退、优先硬解、优先软解、系统 MediaPlayer）以及无损转封装开关。
- **品牌协议精简**：移除荣耀、魅族、努比亚协议分支，精炼保留全品牌通用融合（FUSION）、OPPO/一加、小米/红米、三星 Galaxy、vivo/iQOO、Google Pixel 六种模式。
- **压缩包预览交互提升**：切换条目时异步预加载视频时长，消除 `0.0s / 0.0s` 闪烁；顶部媒体预览条增加点击与横向拖动跳转。
- **多语言本地化**：完整补齐简体中文、繁体中文、English、Русский 四种语言的界面与提示文本。

### English
- **Apple Live Photo Archive Batch Import (`.livp` / `.zip`)**: Stream-scans archives for `HEIC/JPG + MOV/MP4` pairs, standalone videos, and photos; supports inline preview, category filtering, direct save, and batch conversion.
- **QuickTime `.MOV` & Audio Compatibility Fix**: Automatically remuxes QuickTime containers and transcodes raw PCM audio (`lpcm`/`sowt`/`twos`) to standard AAC, fixing muted playback and export issues on Android.
- **Decoder & Stream Processing Settings**: Configurable decoder engine modes in Settings (Auto Fallback, Hardware First, Software First, and System MediaPlayer).
- **Brand Protocol Streamlining**: Streamlined brand modes to Universal Fusion, OPPO/OnePlus, Xiaomi/Redmi, Samsung Galaxy, vivo/iQOO, and Google Pixel.
- **Archive Preview Polish**: Pre-loads video duration to prevent `0.0s / 0.0s` flickering and added tap/drag-to-seek on the preview progress bar.
- **Localization**: Added full language support across `zh-CN`, `zh-TW`, `en`, and `ru`.

---

## [v1.2.2] - 2026-10-01 (Build 12)

### Features
- **Live Photo Gallery**: Automatically scan and view all motion photos stored on your device with smooth grid loading.
- **Press to Play**: Long-press any photo card to instantly play the motion clip with gesture controls and full-screen playback.
- **Video to Motion Photo**: Easily convert regular video clips into motion photos (JPEG + MP4) with customizable cover frames.
- **Pair Photos & Videos**: Select any standalone picture and short video to combine them into standard Live Photo format.
- **Extract Video & Frames**: Export embedded MP4 video or pick individual high-resolution photo frames.
- **XMP Metadata Inspector**: View detailed technical metadata like `MicroVideoOffset` and Google Motion Photo flags.
- **Theme & Appearance**: Full Material You dynamic color theming with light and dark mode support.
- **App Size Optimization**: 64-bit native architecture build (`arm64-v8a`) for small download size and fast loading.

---

## [v1.0.0] - 2026-07-31

### Initial Release
- Initial release of MotionCraft Live Photos Studio for Android.
