# MotionCraft 📸 (Live Photos Studio)

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg?logo=android)](https://www.android.com/)

## 📖 项目简介

**MotionCraft** 是一款专为 Android 打造的高性能跨平台实况照片（Live Photos / Motion Photos）查看、转换、合成与批量管理应用。基于标准的 Motion Photo 与 MicroVideo 规范，深度兼容 Apple iOS Live Photo、Google Motion Photo 以及小米、华为、三星、OPPO、vivo 等主流厂商的实况照片格式。

**主要功能：**
*   **一键合成**：选择一张封面图片和一段视频，快速合成符合 Android 标准的实况图（基于 MicroVideo 规范，如 JPEG 附加 MP4）。
*   **视频提取**：支持从已有的实况图中提取出隐藏的 MP4 视频文件。
*   **本地图库**：内置实况图库管理，支持列表展示已生成的实况图。
*   **沉浸式预览**：支持高斯模糊背景、长按手势交互播放、无缝视频衔接的实况播放体验。

## 🖼️ 项目截图

*(建议在此处替换为您应用的实际截图)*

| 首页图库 | 制作实况 | 播放预览 | 设置主题 |
| --- | --- | --- | --- |
| `<img src="screenshots/library.png" width="200">` | `<img src="screenshots/convert.png" width="200">` | `<img src="screenshots/preview.png" width="200">` | `<img src="screenshots/settings.png" width="200">` |

## ⬇️ 下载

可以在 [Releases](https://github.com/your-username/LivePhoto/releases) 页面下载最新的 APK 安装包。

## 🛠️ 开发环境与技术栈

*   **语言**: Kotlin
*   **UI 框架**: Jetpack Compose (Material Design 3)
*   **媒体处理**: Media3 (ExoPlayer), MediaCodec, MediaMuxer, MediaExtractor
*   **本地存储**: Room Database
*   **异步处理**: Kotlin Coroutines & Flow

**构建要求：**
*   Android Studio Iguana | 2023.2.1 或更高版本
*   JDK 17
*   Android SDK API 34

## ⚠️ 免责声明

1. 本项目仅供**技术交流与学习**目的。
2. 资源内容版权归原作者所有，使用本软件产生的一切后果由使用者自行承担。
