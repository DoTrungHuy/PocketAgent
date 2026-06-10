# AgentPad Architecture

## Product split

AgentPad 采用三个独立边界：

1. `android-app`：原生主应用，负责 UI、模型、计划、审批和 Android 能力。
2. `termux-lite`：已经实机验证的兼容版本，不是主应用依赖。
3. `runtime-apk`：未来独立签名的开发运行时，阶段一不创建可执行实现。

主应用不得下载并直接执行可写目录中的二进制文件，也不得通过降低 target SDK 绕过 Android W^X 安全机制。

## Native layers

- `data`：Room 任务记录、DataStore 设置、Keystore 密钥。
- `domain`：`TaskPlan`、`PlannedAction`、`ApprovalToken`、`ToolResult`、`CapabilityDescriptor`。
- `policy`：本地风险分级、审批约束、永久禁止项。
- `provider`：OpenAI-compatible 模型协议和响应解析。
- `tool`：固定 Schema 的本地工具注册表。
- `ui`：任务中心、审批中心、能力中心和设置。

模型输出永远是建议，不是权限来源。所有工具调用必须经过：

```text
模型输出 -> Schema 校验 -> 本地策略 -> 用户审批 -> 工具执行 -> 结果验证
```

## Stage boundaries

### Native Core

- 对话与计划；
- 用户授权文件读取和总结；
- 应用内审批；
- 基础 Android Intent；
- 本地审计摘要。

### Device Agent

- AccessibilityService；
- 节点观察、点击、输入和滑动；
- 可选视觉模型；
- 持续通知、紧急停止和锁屏暂停。

### Runtime APK

- 签名级 Binder 接口；
- 结构化 `argv`；
- 工作区和环境变量白名单；
- Python、Git、Shell 的许可证与可执行打包验证。

阶段之间只通过稳定接口连接，不能让主应用直接依赖 Termux 私有目录或未知插件代码。
