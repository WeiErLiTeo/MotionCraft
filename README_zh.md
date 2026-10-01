# MotionCraft (Live Photos Studio)

<p align="center">
  <img src="Screenshot/logo.svg" width="100" alt="MotionCraft Logo" />
</p>

<p align="center">
  <b>Android 实况照片 (Live Photos / Motion Photos) 查看、转换、合成与批量管理应用</b>
</p>

<p align="center">
  <a href="README.md"><b>English Documentation (英文版)</b></a>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License"></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-1.9.0-blue.svg?logo=kotlin" alt="Kotlin"></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose" alt="Jetpack Compose"></a>
  <a href="https://www.android.com/"><img src="https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg?logo=android" alt="Platform"></a>
  <a href="CHANGELOG.md"><img src="https://img.shields.io/badge/Version-1.2.2-orange.svg" alt="Version"></a>
</p>

---

## 项目简介

**MotionCraft** 是一款运行于 Android 平台的实况照片 (Live Photos / Motion Photos) 专业工具。
应用提供相册实况照片智能扫描与按压播放、微视频提取、高清单帧截取、视频转实况照片、图片与短视频双选配对合成，以及 XMP 元数据深度诊断功能。

---

## 格式与平台支持

| 平台 / 格式 | 状态 | 说明 |
| :--- | :---: | :--- |
| **Google Motion Photo** | 支持 | 标准 XMP `GCamera:MicroVideo` 与 `MicroVideoOffset` 封装 |
| **Xiaomi 小米实况照片** | 支持 | 小米相册实况照片识别、封面与微视频提取合成 |
| **OPPO / OnePlus 实况照片** | 支持 | ColorOS 实况照片封装结构识别与转换 |
| **抖音实况照片分享 / 识别** | 支持 | 抖音保存及分享的动态实况格式解析与播放 |
| **Apple Live Photo** | 开发中 | iOS HEIC/JPEG + MOV 配对结构兼容拓展 |
| **vivo / iQOO 实况照片** | 开发中 | OriginOS 专属实况格式适配中 |

---

## 核心功能

- **实况图集智能管理**
  - 自动遍历相册检索包含动态微视频 (`MicroVideoOffset`) 的实况照片。
  - 支持网格多选、全选与批量删除管理。
- **动态按压与全屏播放**
  - 长按图片卡片即刻平滑播放微视频，支持全屏预览与手势交互。
- **视频转实况照片**
  - 自定义视频时间轴截取封面帧，一键注入 XMP 元数据并合成为标准 Android 实况照片。
- **双选配对合成**
  - 自由选取独立的静态图片与短视频，自动根据机型规范合成为 Live Photo。
- **封面微视频与单帧提取**
  - 从已有实况照片中无损分离出独立 MP4 视频与 JPEG 封面。
  - 配合动态正弦波浪滑块，逐帧定位并提取保存超清静态单帧。
- **XMP 元数据诊断**
  - 深度解析文件的字节偏移量、命名空间与嵌入标志。
- **多语言与个性化主题**
  - 内置简体中文、繁体中文、英语、日语多语言即时切换。
  - 完整适配 Material Design 3 Monet 动态取色、深色模式与多种强调色方案。
- **开发者与诊断控制台**
  - 内置运行时操作交互日志记录，支持日志复制分享与调试悬浮窗。

---

## 应用界面

| 实况图集 | 视频转实况 | 双选配对 | 系统设置 |
| :---: | :---: | :---: | :---: |
| <img src="Screenshot/01_gallery.png" width="220" alt="实况图集" /> | <img src="Screenshot/02_convert.png" width="220" alt="视频转实况" /> | <img src="Screenshot/03_pairing.png" width="220" alt="双选配对" /> | <img src="Screenshot/04_settings.png" width="220" alt="系统设置" /> |
| 本地实况照片识别与展示 | 视频截取封面与合成 | 图片与视频手动合并 | 基础配置与主题设置 |

---

## 技术原理

Android Motion Photo 格式将 JPEG 封面图与 MP4 视频流整合存储在同一个文件中：

```
+--------------------------------+----------------------------+
|  JPEG Image Data               |  Embedded MP4 Video Data   |
|  (Contains XMP App1 Segment)   |  (At the end of file)      |
+--------------------------------+----------------------------+
  ^                              ^
  |                              |
  +-- MicroVideoOffset Specifies -+
```

1. **XMP 偏移定位**：读取 JPEG 头部 APP1 (`0xFFE1`) 标记，解析 `GCamera:MicroVideoOffset` 获取末尾 MP4 视频流的起始字节位置。
2. **视频提取**：通过 `RandomAccessFile` 基于偏移量精确定位并提取尾部嵌入的 MP4 视频流。
3. **播放控制**：基于 AndroidX Media3 (ExoPlayer) 与 Jetpack Compose 视图绑定，实现无缝手势触发与状态控制。

---

## 项目结构

```text
MotionCraft/
├── app/                                    # Android 应用主模块
│   ├── build.gradle.kts                    # 模块级构建脚本 (纯 64 位 ABI 过滤、混淆压缩)
│   ├── proguard-rules.pro                  # R8 / ProGuard 混淆规则
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml         # 应用清单元数据与权限声明
│       │   ├── java/com/example/
│       │   │   ├── MainActivity.kt         # 应用主 Activity (Edge-to-Edge 边到边)
│       │   │   ├── core/                   # 底层媒体引擎与格式协议
│       │   │   │   ├── converter/
│       │   │   │   │   └── LivePhotoToolConverter.kt # 实况照片转换逻辑
│       │   │   │   ├── media/
│       │   │   │   │   ├── ImageProcessor.kt         # 封面与单帧提取图像处理器
│       │   │   │   │   ├── VideoProcessor.kt         # 视频转码与裁切处理
│       │   │   │   │   └── VideoThumbnailCache.kt    # 帧缓存与缩略图加载
│       │   │   │   └── protocol/
│       │   │   │       ├── LivePhotoBrandMode.kt     # 厂商模式枚举 (Google/小米/OPPO等)
│       │   │   │       └── LivePhotoProtocolPacker.kt# XMP 注入与视频拼装打包器
│       │   │   ├── data/
│       │   │   │   └── AppDatabase.kt      # Room 数据库 (实况照片持久化索引)
│       │   │   ├── ui/                     # Jetpack Compose UI 界面
│       │   │   │   ├── LivePhotoApp.kt     # 应用脚手架与沉浸式导航栏
│       │   │   │   ├── components/         # 独立功能 UI 组件
│       │   │   │   │   ├── DebugLogSheet.kt          # 日志控制台抽屉
│       │   │   │   │   ├── LivePhotoCard.kt          # 图集网格实况卡片
│       │   │   │   │   ├── LivePhotoPlaybackOverlay.kt# 按压全屏动态播放层
│       │   │   │   │   ├── SquigglyWavySlider.kt     # 正弦波浪起伏时间轴滑块
│       │   │   │   │   ├── VideoPlayerComponents.kt  # 播放器核心封装
│       │   │   │   │   └── VideoTrimSlider.kt        # 视频裁切与区间选择器
│       │   │   │   ├── screens/            # 应用一级与二级页面
│       │   │   │   │   ├── ConvertScreen.kt          # 视频转实况页面
│       │   │   │   │   ├── LibraryScreen.kt          # 实况照片图集主页
│       │   │   │   │   ├── LivePhotoDetailScreen.kt  # 详情播放与单帧提取页
│       │   │   │   │   ├── ManualPairScreen.kt       # 双选配对合成页
│       │   │   │   │   ├── SettingsScreen.kt         # 系统配置与主题页
│       │   │   │   │   └── XmpToolScreen.kt          # XMP 元数据诊断页
│       │   │   │   └── theme/              # Material Design 3 主题系统
│       │   │   │       ├── Color.kt                  # 调色板定义
│       │   │   │       ├── Theme.kt                  # 动态取色与深浅配色方案
│       │   │   │       └── Type.kt                   # 字体排版规格
│       │   │   ├── util/                   # 辅助工具模块
│       │   │   │   ├── DebugLogManager.kt  # 运行时日志记录与分享
│       │   │   │   └── MotionPhotoHelper.kt# XMP 解析与实况文件探测器
│       │   │   └── viewmodel/              # 业务状态管理
│       │   │       └── LivePhotoViewModel.kt # 全局实况状态 ViewModel
│       │   └── res/                        # 应用资源
│       │       ├── drawable/               # 自定义矢量图标
│       │       ├── mipmap-anydpi-v26/      # 自适应圆角应用图标
│       │       ├── values/                 # 基础主题与默认字符
│       │       ├── values-en/              # 英语字符串
│       │       ├── values-ja/              # 日语字符串
│       │       ├── values-zh-rCN/          # 简体中文字符串
│       │       ├── values-zh-rTW/          # 繁体中文字符串
│       │       └── xml/                    # FileProvider 与数据备份策略
│       └── test/                           # 单元测试与 Robolectric 测试
├── .github/                                # GitHub Actions 持续集成与发布配置
│   ├── ISSUE_TEMPLATE/                     # 社区议题反馈模板
│   │   ├── bug_report.md                   # 异常缺陷报告模板
│   │   └── feature_request.md              # 新功能建议模板
│   └── workflows/                          # 自动化工作流
│       ├── ci.yml                          # 持续集成构建验证流水线
│       └── release.yml                     # 自动编译 64 位 APK 并生成 GitHub Release
├── gradle/
│   └── libs.versions.toml                  # 依赖版本管理 Version Catalog
├── Screenshot/                             # 项目展示截图目录
│   └── logo.svg                            # 应用白底矢量 Logo
├── CONTRIBUTING.md                         # 贡献指南
├── CHANGELOG.md                            # 版本更新日志
├── SECURITY.md                             # 安全响应政策
├── LICENSE                                 # Apache 2.0 开源协议
├── build.gradle.kts                        # 根工程构建配置
├── gradle.properties                       # Gradle JVM 构建参数
├── settings.gradle.kts                     # Gradle 模块声明
├── README_zh.md                            # 简体中文说明文档
└── README.md                               # 英文说明文档
```

---

## 构建说明

使用 Gradle 编译 Release APK：

```bash
./gradlew assembleRelease
```

构建产物目录：`app/build/outputs/apk/release/`

---

## 系统要求与权限

### 系统要求
- **系统版本**：Android 8.0 (API Level 26) 及更高版本
- **处理器架构**：`arm64-v8a` / `x86_64`（纯 64 位高性能构建，单包体积仅约 3.4MB）

### 权限说明
- `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO`：Android 13+ 本地实况照片及视频读取
- `READ_EXTERNAL_STORAGE`：Android 12 及以下读取本地相册媒体文件
- `WRITE_EXTERNAL_STORAGE`：Android 9 及以下保存生成的实况照片到相册

---

## 开源协议

本项目基于 [Apache 2.0 License](LICENSE) 协议开源。
