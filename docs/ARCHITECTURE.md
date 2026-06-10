# AgentPad Architecture

## Product boundary

AgentPad 当前由两个已存在部分组成：

1. `android-app`：原生主应用，负责线程、模型、计划、审批、执行、诊断和 Android 能力。
2. `termux-lite`：早期真机验证原型，仅作为兼容与研究基线。

未来的 Web、桌面端或独立 Runtime 都不是 `v0.2.1` 的运行依赖。主应用不得下载后直接执行可写目录中的二进制，也不得通过降低 target SDK 绕过 Android 安全机制。

## Thread model

一个本地工作区包含多个 `AgentThread`。每个线程包含：

- `AgentTurn`：不可变的用户回合与该回合计划。
- `ThreadMessage`：用户目标、计划摘要、结果、状态和上下文检查点。
- `ThreadAttachment`：用户通过系统选择器授权的 URI 与元数据。
- `AuditEvent`：本地工具、状态与审批事件摘要。

追加追问时创建新回合。尚未执行的旧计划标记为 `SUPERSEDED`，审批令牌不会持久化，因此进程重启、参数变化或新回合都会使旧授权失效。运行中的回合必须完成或取消后才能追问。

Room v2 使用线程、回合、消息、附件和审计表。v1 的 `tasks` 会迁移为同 ID 的线程和第一个历史回合，保留目标、计划 JSON、结果和审计；活跃状态迁移为 `INTERRUPTED`。

## Context protocol

规划请求包含：

- 当前目标
- 线程请求上下文
- 附件元数据
- 固定工具 Schema
- 当前服务商与模型配置

文件原文不进入普通线程消息。当请求上下文超过 60 条消息或约 48,000 字符时，界面要求用户确认压缩。摘要作为 `CONTEXT_SUMMARY` 检查点保存，原始历史仍留在 Room；后续请求只发送最新检查点和其后的完整消息。

## Execution pipeline

```text
用户目标
  -> 模型生成结构化计划
  -> PlanParser 严格校验
  -> 本地风险升级
  -> 任务级/动作级审批
  -> 单次令牌校验
  -> 工具执行
  -> 结果验证
  -> 本地审计
```

未知工具、禁止工具、畸形动作和超过八步的计划直接拒绝，不允许静默跳过。模型输出永远不是权限来源。

## Native layers

- `domain`：线程、回合、消息、附件、计划、动作、审批令牌和工具结果。
- `data`：Room v2、DataStore、计划序列化和线程仓库。
- `security`：Android Keystore API Key 存储。
- `policy`：工具白名单、风险升级、审批范围和参数摘要。
- `provider`：DeepSeek 与自定义 OpenAI-compatible 协议、上下文策略。
- `tool`：固定 Android 工具注册表。
- `diagnostics`：本地崩溃捕获、脱敏和导出。
- `ui`：线程时间线、计划检查器、审批、能力和设置。

## Responsive UI

- `<600dp`：单栏，计划、审批和设置是独立页面。
- `600–999dp`：线程侧栏 + 内容区。
- `>=1000dp`：线程侧栏 + 时间线 + 计划/审批检查器。

每个区域只有一个纵向滚动容器，禁止 `LazyColumn` 嵌套造成无限高度约束。默认使用浅色高对比平面视觉，深色主题可选。

## Recovery and diagnostics

应用启动时把未完成的 `PLANNING`、`RUNNING`、`VERIFYING` 回合改为 `INTERRUPTED`。用户必须重新检查计划并审批后才能继续。

未捕获异常仅写入应用私有目录。报告包含构建身份、设备/API、窗口宽度、最后页面、无消息文本的堆栈帧和最近审计摘要；不记录 API Key、文件原文或完整模型输出。用户可通过系统文件创建器主动导出。
