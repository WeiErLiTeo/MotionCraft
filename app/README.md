# MotionCraft - 跨平台实况照片管理与转换工具

**MotionCraft** 是一款专为 Android 打造的高性能、沉浸式实况照片（Live Photos / Motion Photos）查看、转换与合成工具。支持 Apple iOS Live Photo、Google Motion Photo、小米/华为/三星/OPPO/vivo 等主流厂商的实况照片解析与播放。

---

## 🌟 核心功能亮点

- 📸 **实况图集管理 (Live Photo Gallery)**
  - 自动识别并呈现包含微视频 (Micro Video) 的实况照片。
  - 支持**长按开启批量选择模式**，多选批量删除及网格动画。
- 🎬 **沉浸式播放器 (Immersive Live Playback)**
  - 全屏沉浸式无边框播放，支持动态卡片弹簧缩放 (Spring Animation) 与实时高斯模糊背景。
  - 自动隐去状态栏与导航栏，带来纯粹的视觉享受。
- 🔄 **视频与实况互转 (Video / Photo Conversion)**
  - 将普通视频或动态图像合成标准化 Android Motion Photo。
  - 支持多帧裁剪、Cover 图选择与帧率调优。
- 🔗 **双选配对 (Manual Asset Pairing)**
  - 将独立的静态图片 (JPEG/PNG) 与动态短视频 (MP4) 手动配对合成为单文件 Live Photo。
- 🛠️ **XMP 诊断与缓存管理 (XMP Tools & Cache Cleaning)**
  - 内置 XMP 元数据检测工具，可深入分析实况照片的 `MicroVideoOffset` 与结构属性。
  - 支持一键清理缓存与内存优化。

---

## 🔬 底层实况照片技术原理 (Technical Deep Dive)

Android 与 iOS 中的实况照片 (Motion Photo / Live Photo) 本质上是将**高分辨率静态封面图 (JPEG)** 与**短视频流 (MP4)** 封存在同一个文件中的复合媒体格式。本项目基于以下关键技术实现高效的解析、提取与合成：

### 1. XMP 元数据解析与偏移计算 (XMP Metadata & Offset Detection)
- **标准规格**: 遵循 Google Motion Photo 与 ISO/IEC 16684-1 XMP 规范。
- **元数据标签**: 解析包含 `GCamera:MicroVideo`、`GCamera:MicroVideoOffset`、`Camera:MotionPhoto` 以及 `MotionPhotoPresentationTimestampUs` 等属性。
- **文件流读取**: 通过高效扫描 JPEG 文件的 `APP1` Marker段 (`0xFFE1`)提取 XMP XML 数据，准确定位嵌入 MP4 视频流在整个文件末尾的字节偏移量 (`Offset`)。

### 2. 无损视频提取与零延迟播放 (Micro Video Extraction)
- **二进制提取**: 获取 `MicroVideoOffset` 后，使用 `RandomAccessFile` 跳过静态图片数据区，准确截取文件尾部的 MP4 字节流并写入缓存区。
- **Media3 ExoPlayer 集成**: 提取后的视频直接交由 Android Standard Media3 ExoPlayer 进行硬解码，结合 Jetpack Compose `AndroidView` 实现无缝封面图与视频播放交替。

### 3. 实况照片合成与 XMP 注入 (Motion Photo Synthesis & Injection)
- **数据封装**: 在合成实况照片时，将选定的静态图片与 MP4 视频流按二进制顺序拼接。
- **XMP Header 注入**: 计算 MP4 视频流的总字节大小，构造规范的 XMP 元数据 XML，并将其插入到 JPEG 的 `APP1` 标头段中。使生成的文件能被 Google 相册、小米相册、华为相册等原生系统相册识别为标准的实况照片。

---

## 🛠️ 技术栈 (Tech Stack)

- **语言**: Kotlin (100%)
- **UI 框架**: Jetpack Compose (Material Design 3 + Dynamic Color)
- **多媒体处理**: AndroidX Media3 (ExoPlayer) + Coil Image Loader
- **数据库与持久化**: Room Database + Jetpack DataStore
- **并发与异步**: Kotlin Coroutines + Flow
- **架构模式**: MVVM + Clean Architecture

---

## 📱 系统要求

- **最低支持**: Android 8.0 (API Level 26)
- **推荐版本**: Android 12+ (API Level 31+) 以获得最佳 Dynamic Color 与 Edge-to-Edge 沉浸体验。
