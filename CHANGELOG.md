# 更新日志 (Changelog)

本项目遵循 [Semantic Versioning 2.0.0](https://semver.org/lang/zh-CN/) 规范。

---

## [v1.0.0] - 2026-07-31

### 🌟 新增功能
- **实况图集管理**：支持扫描包含 MicroVideo 偏移量的 Motion Photo 媒体文件，支持多选与批量删除。
- **实况照片播放**：基于 ExoPlayer 实现实况视频按压播放与全屏预览。
- **视频与实况互转**：支持选择视频切片导出封面并生成 Motion Photo (JPEG + MP4)。
- **图片与视频合成**：支持选择独立图片与视频，注入 XMP 元数据并生成 Live Photo。
- **XMP 元数据诊断**：解析与查看实况照片的 `MicroVideoOffset` 与 `GCamera:MicroVideo` 参数。

### 🛠️ 技术与架构
- 基于 Jetpack Compose 与 Material Design 3 构建应用界面，支持动态调色。
- 适配 Android 12+ 边到边（Edge-to-Edge）显示。
- 使用 `RandomAccessFile` 进行文件字节偏移定位与视频提取。
