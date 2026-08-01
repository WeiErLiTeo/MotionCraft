# 更新日志 (Changelog)

本项目遵循 [Semantic Versioning 2.0.0](https://semver.org/lang/zh-CN/) 语义化版本规范。

---

## [v1.0.0] - 2026-07-31

### 🌟 新增功能 (Features)
- 📸 **实况图集管理**: 自动识别包含 MicroVideo 偏移量的 Motion Photo 媒体文件，支持长按网格批量多选与一键删除。
- 🎬 **沉浸式实况播放器**: 支持基于 ExoPlayer 硬解码无缝播放实况短视频，配以实时动态高斯模糊卡片背景与 Spring 弹簧缩放效果。
- 🔄 **视频与实况互转**: 支持导入短视频或独立封面，合成标准格式 Android Motion Photo (JPEG + MP4)。
- 🔗 **双选配对合成**: 允许用户手动配对静态图片与短视频文件并注入 XMP 元数据，合成为单一 Live Photo 文件。
- 🛠️ **XMP 元数据诊断**: 深度检测分析实况照片的 `MicroVideoOffset`、`GCamera:MicroVideo` 等标准 XMP 头部标签。

### 🛠️ 技术与性能改进 (Improvements)
- 使用 Jetpack Compose + Material Design 3 全面重构 UI 架构，支持 Dynamic Color 动态调色盘。
- 针对 Android 12+ 适配 Edge-to-Edge 沉浸式边到边布局。
- 优化文件 IO 读取性能，引入 `RandomAccessFile` 秒级提取视频流字节偏移。
