# AgentPad

AgentPad 是一个面向中国大陆网络环境、同时适配安卓手机和平板的开源 AI Agent 工作台。

项目目标不是再做一个聊天框，而是在 Android 安全机制允许、且用户明确授权的范围内，让 Agent 能够制定任务计划、解释所需权限、等待审批并调用设备能力完成工作。

## 当前状态

项目正在从已验证的 Termux 原型迁移到独立原生 APK。

| 版本 | 状态 | 目标 |
| --- | --- | --- |
| `v0.1.0-dev` Termux Lite | 两台平板核心链路已验证 | 保留兼容版本与技术基线 |
| `v0.2.0-alpha` Native Core | 开发中 | 独立 APK、模型配置、任务计划、审批和授权文件处理 |
| `v0.3.0-alpha` Device Agent | 规划完成 | 无障碍跨应用操作和可选视觉理解 |
| `v0.4.0-alpha` Runtime | 可行性验证待办 | 独立签名 Runtime APK，提供受限开发工具 |

现有 Termux 版本位于 [`termux-lite/`](termux-lite/README.md)。它不会被删除，但不再是主产品。

## 原生 MVP

`android-app/` 当前实现的阶段一目标：

- Kotlin + Jetpack Compose + Material 3；
- 手机单栏、平板双栏的自适应工作台；
- DeepSeek 与自定义 OpenAI-compatible 接口；
- Android Keystore 加密保存 API Key；
- DataStore 保存普通模型设置；
- Room 保存任务和审计摘要；
- 任务计划、状态流转和审批策略；
- 系统文件选择器读取用户明确授权的文本文件；
- 固定工具 Schema、步骤与输出上限；
- 不申请全盘文件权限，不依赖 Google Services。

阶段一不会假装提供尚未完成的能力：完整 Shell、Python、Git Runtime、Shizuku 和跨应用自动点击均不属于 `v0.2.0-alpha`。

## 安全边界

- 普通读取可自动执行。
- 外部影响操作按任务批准。
- 发送、删除、覆盖、跨应用输入、截图上传、安装和命令执行逐项批准。
- 支付、密码、验证码、绕过锁屏、静默安装和访问其他应用私有数据永久禁止。
- 模型只能提出操作，风险等级由本地策略引擎决定。
- API Key 不进入 Room、日志、Git 或普通配置文件。

详细设计见：

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- [`docs/SECURITY.md`](docs/SECURITY.md)
- [`docs/ROADMAP.md`](docs/ROADMAP.md)

## 构建

当前电脑已发现：

- OpenJDK 17：`C:\Program Files (x86)\Android\openjdk\jdk-17.0.14`
- Android SDK：`C:\Program Files (x86)\Android\android-sdk`
- Android Platform / Build Tools 35

运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-android.ps1 -ChinaMirrors
```

脚本会优先使用项目内 `.tools\gradle-*`，其次使用系统 `gradle`。不传 `-ChinaMirrors` 时使用官方 Maven 源；国内镜像不会静默启用。正式 `v0.2.0-alpha` 发布前必须升级并验证 `compileSdk/targetSdk 36`。

## 发布原则

- 本地实机验收通过后再创建公开 GitHub 仓库。
- GitHub Releases 是首发渠道，不依赖 Google Play。
- APK 安装和更新始终保留 Android 系统确认。
- 发布物包含 APK、SHA256、签名指纹、SBOM 和第三方许可证。

项目当前采用 MIT License。未来独立 Runtime APK 的许可证将根据其包含的软件单独确定，不会用主项目 MIT License 掩盖第三方义务。
