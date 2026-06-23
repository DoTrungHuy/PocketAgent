# AgentPad Roadmap

## v0.1.0-dev: Termux Lite

- 已在目标平板完成模型 API 与 Web 原型链路验证。
- 冻结在 `termux-lite/`，继续接受安全修复，但不再作为主产品。

## v0.2.0-alpha.1: Native Core baseline

- 首个正式签名原生 APK。
- 建立 Keystore、DataStore、Room、固定工具 Schema、本地风险升级和审批令牌。
- 建立 GitHub Release、SHA256、签名证书记录与 CycloneDX SBOM。

## v0.2.1-alpha.1: Thread workspace

- 真实多线程、多回合模型。
- Room v1→v2 自动迁移和中断恢复。
- 上下文压缩确认与检查点。
- 手机单栏、中型平板双栏、大屏三栏。
- 浅色/深色主题和可选隐私模式。
- 本地脱敏崩溃诊断。
- 只发布可从 v0.2.0 同签名升级的正式签名预览 APK。

## v0.2.x: Native Core hardening

- `v0.2.2-alpha.1` 聚焦简洁工作台：Chat、Tasks、Settings 三页结构，任务审批内联到任务卡。
- 国内模型服务商预设、错误分类、取消、重试和限流提示。
- 更完整的任务恢复、活动记录和数据清理。
- 步骤、API 调用、时长与输出上限。
- 手机、横屏、字体缩放和减少动画适配。
- 在小米平板 7S Pro、联想小新 Pad 2024 与 ARM64 手机上持续验收安装、重启和升级。

## v0.3.0-alpha: Device Agent

- 按需启用 AccessibilityService。
- 节点观察、点击、输入、滑动、返回和执行后验证。
- 节点不足时逐次审批截图，并发送到独立配置的视觉模型。
- 密码、验证码、支付字段、锁屏和受保护窗口永久禁止操作。
- 持续通知、当前步骤、暂停和紧急停止。
- Shizuku 仅作为实验增强，不作为安装条件。

## v0.4.0-alpha: Runtime feasibility

- 研究独立签名的 `AgentPad Runtime APK`。
- 主应用只通过签名级权限和 Binder 调用 Runtime。
- 接口仅接受结构化 `argv`、工作目录、超时、环境变量白名单和输出上限。
- 验证 ARM64 Python、Git、Shell 的来源、补丁、许可证和 SBOM。
- 可行性失败时继续使用 Termux Lite，不降低 target SDK。

## Later forms

Web、桌面端、跨设备同步和独立 Runtime 属于第三、第四阶段研究。它们必须复用同一线程/审批语义，但不会塞入 `v0.2.1` 单 APK，也不会让云端成为本地权限来源。

## v1.0.0

不以功能数量为标准。只有权限边界、任务恢复、更新安全、故障诊断和三类真机稳定性达到发布门槛后，才进入 `v1.0.0`。
