# AgentPad Roadmap

## v0.1.0-dev: Termux Lite

- 两台目标平板完成模型 API 和 Web 核心链路验证。
- 冻结在 `termux-lite/`，继续提供安全修复但不扩展为主产品。

## v0.2.0-alpha: Native Core

发布门槛：

- 独立 APK，不要求 Termux；
- Android Keystore、DataStore、Room；
- DeepSeek 与自定义 OpenAI-compatible；
- 任务计划、审批中心和审计摘要；
- 系统文件选择器、文本读取与总结；
- 手机和平板自适应；
- 小米平板 7S Pro、联想小新 Pad 2024 和一台 ARM64 手机通过实机测试；
- `compileSdk/targetSdk 36`；
- GitHub Actions 构建、测试、SHA256 和 SBOM。

## v0.3.0-alpha: Device Agent

- 无障碍权限引导和能力检测；
- 节点观察、点击、输入、滑动和返回；
- 执行后重新观察并验证结果；
- 节点不足时逐项批准截图上传到用户配置的视觉模型；
- 持续通知、紧急停止、锁屏暂停和厂商后台恢复指引；
- Shizuku 仅作为实验性可选能力。

## v0.4.0-alpha: Runtime

- 先完成现代 target SDK 下的签名 Runtime APK 可行性验证；
- Binder 结构化执行接口；
- ARM64 Python、Git 和 Shell 的来源、补丁、许可证与 SBOM；
- 命令策略、逐项审批、工作区隔离和输出限制；
- 验证失败时不降低 target SDK，Termux Lite 继续作为开发后端。

## v1.0.0

不以功能数量作为标准。只有权限边界、任务恢复、更新安全、三类真机兼容性和故障诊断稳定后才发布。
