# Changelog (更新日志)

## v1.3.4 (Build 32)
- 精简品牌协议选项：移除荣耀、魅族、努比亚协议分支，保留全品牌通用、OPPO/一加、小米/红米、三星、vivo/iQOO、Google Pixel 六种模式。
- 补全多语言翻译：补齐设置页解码器选项、压缩包导入页面及时间轴组件的简中、繁中、英文、俄文字符串资源。
- 优化压缩包预览交互：在切换压缩包内视频时预先读取时长避免闪烁 `0.0s / 0.0s`，并为顶部预览卡片底部进度条增加点击与拖动跳转支持。
- English: Removed Honor, Meizu, and Nubia brand modes (keeping Universal, OPPO, Xiaomi, Samsung, vivo, and Google Pixel); completed localization across `zh-CN`, `zh-TW`, `en`, and `ru`; added interactive scrubbing and pre-loaded duration to the archive preview bar.

## v1.3.3 (Build 31)
- 修复苹果 `.MOV` 视频无法播放与导出无声问题：
  - 修复伪装成 `isom` 但内部含 QuickTime `wave/lpcm/sowt` 音轨的视频被跳过转封装的问题。
  - 新增 `lpcm`/`sowt`/`twos`/`in24` 原始 PCM 音轨转码为标准 AAC (`mp4a`) 的处理流程。
  - 修复部分 iPhone 实况视频 `edts/elst` 偏移量过大导致音画被截断的问题。
  - 实况详情页与全屏播放器在遇到非标准内嵌视频流时自动修复为标准 MP4 后再播放或导出。

## v1.3.2 (Build 30)
- 新增设置页视频解码策略切换（自动回退、优先硬解、优先软解）与 ZLC 流规范化开关。
- 优化压缩包导入页面的竖屏视频预览布局，固定 16:9.4 预览窗口并居中自适应显示。

## v1.2.9 (Build 27)
- 优化苹果 `.livp` / `.zip` 压缩包流式扫描与按需缩略图加载，支持包含数百个条目的大型压缩包导入。
