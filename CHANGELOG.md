# Changelog (更新日志)

All notable changes to MotionCraft will be documented in this file.

---

## [v1.3.6] - 2026-11-16 (Build 34)

### 新增与优化 (Features & Improvements)
- **内置选择器与批量队列预览全面补全**：
  - 内置文件选择器支持单图查看、视频播放与实况动图直接抽出内嵌 MP4 播放，长按网格或点击左上角图标即可全屏预览与选中/取消。
  - 批量视频转实况队列中的每个视频卡片现已显示真实抽帧缩略图，点击即可直接播放片段预览，或一键转入单独精修裁剪。
- **内置选择器状态栏沉浸与界面优化**：独立配置对话框窗口边缘到边缘透明状态栏与导航栏（适配刘海/挖孔安全区），收紧顶部操作栏并改为可折叠搜索框，显著提升内容展示面积。
- **多厂商实况照片自动识别与标注**：新增 `LivePhotoDetector` 对图片头部 XMP/EXIF 与尾部盒结构进行高速异步探测，兼容识别 OPPO/一加/realme、小米/红米、vivo/iQOO、三星 Galaxy、华为/荣耀、魅族与 Google Pixel 实况照片，并在选择器网格打上品牌徽章与“仅实况”筛选标签。
- **全新“品牌互转”底栏入口与页面合并**：
  - 将原第三栏的“双选封面+视频配对”和“多图合成实况”合并进“视频转实况”页顶部模式切换中；删除“按整个文件夹导入”繁琐入口。
  - 底部第三栏全新上线“品牌互转（Brand Sync）”，支持单张/批量跨品牌无损转换，转换时自动清理旧厂 XMP/MPF/尾部标记并保留原始 EXIF/GPS 与原图画质，支持转换前后预览并导出至 `Pictures/LivePhotos` 及实况库。

### English
- **In-App Picker & Batch Queue Previews**: Added interactive full-screen preview inside the built-in media picker (plays videos and embedded Live Photo MP4 streams directly), plus real frame thumbnails and tap-to-preview playback (with one-tap jump to single-video trimmer) on every batch video queue card.
- **Immersive Status Bar & Picker UI Polish**: Configured edge-to-edge transparent status/navigation bars and display cutout support on `DialogWindowProvider`, compacted the header, and made the search bar collapsible.
- **Cross-OEM Live Photo Auto-Detection**: Added fast async head/tail detection across OPPO/OnePlus/realme, Xiaomi/Redmi, vivo/iQOO, Samsung Galaxy, Huawei/Honor, Meizu, and Google Pixel Live Photos, displaying OEM badges on thumbnails and a `Live Only` filter chip.
- **New Brand Sync Tab & Consolidated Convert Workflows**: Moved dual-select pairing and multi-image synthesis into the Video to Live tab's top mode switcher, removed the whole-folder tree import option, and introduced the new **Brand Sync** tab for lossless cross-brand Live Photo conversion while preserving original EXIF/GPS and embedded video.

---

## [v1.3.5] - 2026-11-16 (Build 33)

### 新增与优化 (Features & Improvements)
- **内置主题适配文件选择器（不抹除照片元数据）**：新增应用内媒体浏览器，直接读取 `MediaStore` 全目录与文件列表，支持按文件夹切换、关键词搜索与多选，并通过 `ACCESS_MEDIA_LOCATION` 与 `MediaStore.setRequireOriginal` 保留原始 EXIF、GPS 与实况尾部视频流，解决系统照片选择器隐藏文件夹及裁剪元数据的问题；可在设置页“文件选择器与元数据保护”随时开关。
- **视频转实况双入口与批量队列**：提供“默认相册/内置多选入口”和“系统文件直选/整文件夹导入入口”；选择多段视频或整个文件夹时自动进入批量转换队列，支持设置 3 秒 / 5 秒 / 完整(≤15s)截取时长、首帧或中间帧封面与协议格式，一键批量生成并存入相册。
- **首次启动权限与引导向导**：首次打开应用自动进入双步骤引导页，集中申请本地照片、视频读取与原始元数据权限（支持直达系统设置补全），并可一键确认内置文件选择器与机型协议偏好；后续可在设置页随时重新打开。
- **图库、配对与 XMP 工具直读增强**：图库支持多选导入与整文件夹批量导入实况照片，手动配对与 XMP 分析页同样接入内置选择器及直选文件入口，防止诊断或编辑前被系统选择器抹除数据。

### English
- **Theme-Adapted Built-in Media Picker (No Metadata Stripping)**: Added an in-app MediaStore folder browser supporting directory filtering, filename search, multi-select, and direct original URI reads (`ACCESS_MEDIA_LOCATION` + `MediaStore.setRequireOriginal`), resolving missing folders and stripped EXIF/XMP/Live Photo trailers caused by Android PhotoPicker. Toggleable in Settings.
- **Dual Video Entries & Batch Video-to-LivePhoto Queue**: Added both a default gallery/built-in picker entry and a direct File / Folder (`OpenMultipleDocuments` / `OpenDocumentTree`) entry in the Convert tab; selecting multiple videos or a folder opens a batch conversion queue with clip duration presets (3s / 5s / Full ≤15s) and cover frame options.
- **First-Launch Permission & Setup Wizard**: Added a 2-step onboarding flow on first launch to request full local video, photo, and media-location permissions (with Settings fallback) and confirm built-in picker & brand protocol defaults.
- **Library, Manual Pair & XMP Metadata Protection**: Added multi-photo and folder batch import to Library, and wired built-in picker + direct document file entries into Manual Pair and the XMP Inspector.

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
