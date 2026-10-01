# 贡献指南 (Contributing Guide)

感谢您对 **MotionCraft** 项目的关注！我们非常欢迎并鼓励来自社区的各种贡献，包括但不限于提交 Bug、改进文档、提交 Feature Proposal 以及贡献代码。

---

## 🚀 快速上手 (Getting Started)

1. **Fork 本仓库** 到您自己的 GitHub 账号。
2. **Clone 仓库** 到本地开发环境：
   ```bash
   git clone https://github.com/your-username/MotionCraft.git
   cd MotionCraft
   ```
3. 在 **Android Studio (Ladybug / Iguana 或更高版本)** 中打开项目。
4. 确保本地已配置 **JDK 17** 及 **Android SDK API 34**。
5. 运行 Gradle Sync 并确保能够成功构建：
   ```bash
   ./gradlew assembleDebug
   ```

---

## 📝 提交 Issue (Filing Issues)

在提交 Issue 前，请搜索已有 Issue 列表以确认该问题未被重复提报：

- 🐞 **Bug 报告**: 请明确描述复现步骤、期望行为、实际行为以及设备系统信息（Android 版本、厂商 UI 版本等）。
- 💡 **功能建议**: 请清晰阐述该功能的使用场景与价值。

---

## 🔀 分支与 Pull Request 规范 (PR Guidelines)

1. 从 `main` 分支拉取新的特性分支：
   ```bash
   git checkout -b feature/your-feature-name
   # 或
   git checkout -b fix/your-bug-fix
   ```
2. **代码风格与规范**：
   - 遵循 Kotlin 官方代码风格规范与 Compose 最佳实践。
   - 尽量保持 Commit Message 简洁清晰（推荐使用 Conventional Commits 规范，如 `feat: ...`, `fix: ...`, `docs: ...`）。
3. **提交 PR**：
   - 提交 Pull Request 至 `main` 分支。
   - 详细说明本次 PR 修改的内容及测试情况。

---

## 📄 开源协议 (License)

参与贡献即表示您同意将您的代码以 [Apache-2.0 License](LICENSE) 协议进行开源。
