# AgentPad

AgentPad 是面向中国大陆网络环境、同时适配 Android 手机和平板的开源手机 Agent。它用类似 GPT 的聊天入口承载 Agent 能力：每次打开默认进入新对话，历史对话保留在侧栏；在用户授权后，Agent 可以读取当前会话允许的手机文件和图片作为上下文。

## 下载

当前正式签名预发布版：

[直接下载 AgentPad v0.2.3-alpha.1 APK](https://github.com/DoTrungHuy/AgentPad/releases/download/v0.2.3-alpha.1/AgentPad-v0.2.3-alpha.1.apk)

[查看发布说明、SHA256、签名证书指纹和 SBOM](https://github.com/DoTrungHuy/AgentPad/releases/tag/v0.2.3-alpha.1)

Android 会显示标准安装确认。AgentPad 不要求安装 Termux、Google Play、Google 登录或 Google Services。升级包继续使用 v0.2.0 的正式签名，可覆盖安装并保留本地数据。

> `v0.2.3-alpha.1` 是预发布测试版本。长期下载入口只指向正式签名 APK；名称含短 Git SHA 的 Debug 产物仅供开发排错。

`v0.2.3-alpha.1` 重点改成移动端 Agent 对话入口，保留左侧历史、新对话、模型设置和聊天框内按需读取手机内容。

## v0.2.3 开发重点

- GPT 式 Agent 入口：启动默认新对话，历史对话保留在左侧。
- 主界面收敛为 Chat 和 Settings，不再把用户强制带入任务工作流。
- Agent 可自动读取当前会话已授权内容；文本文件读取正文，图片以模型可接收的 image data URI 传入。
- 文件/图片访问通过 Android 系统选择器授权，入口放在聊天框旁边。
- 可选视觉模型 endpoint/model，用于图片理解。

## v0.2.2 上一版重点

- 当时尝试了简洁工作台：左侧会话、中央聊天、固定底部输入区，大屏不再堆叠复杂右栏。
- 当时主页面曾收敛为 Chat、Tasks、Settings；v0.2.3 已继续收敛为移动端 Agent 对话入口。
- Provider 错误分类：限流、网络超时、可重试服务异常、服务拒绝和响应结构异常。
- 国内模型服务商预设：DeepSeek、阿里云百炼/通义、Kimi、智谱 GLM、MiniMax、硅基流动、火山方舟/豆包、百度千帆和自定义 OpenAI-compatible。
- 暂不展示流式输出、附件中心、能力页或设备自动化入口，避免主体验变重。
- 保留底层安全边界：模型只生成计划，本地代码继续负责风险归类、审批和执行。

## v0.2.1 重点

- 单工作区支持多个真实任务线程，每个线程可连续追问。
- 每次追问创建不可变的新回合和新计划；未执行旧计划会标记为 `SUPERSEDED`，旧审批立即失效。
- 异常退出后，`PLANNING`、`RUNNING`、`VERIFYING` 回合恢复为 `INTERRUPTED`，必须重新审批。
- Room v2 保存线程、回合、消息、附件和审计；v0.2.0 旧任务自动迁移为历史回合。
- 完整历史保存在本机。超过 60 条消息或约 48,000 字符时，先征得确认再创建上下文检查点，原始消息不删除。
- 文件历史只保存元数据，文件原文不会进入普通消息或上下文压缩请求。
- 顶栏始终显示服务商和模型；仅保留 DeepSeek 与自定义 OpenAI-compatible 配置。
- `<600dp` 单栏、`600–999dp` 双栏、`>=1000dp` 三栏；每个页面只有一个纵向滚动容器。
- 默认浅色高对比主题，可切换深色；截图默认允许，隐私模式才动态启用 `FLAG_SECURE`。
- 本地崩溃报告支持导出，包含版本、Git SHA、设备、窗口宽度、最后页面、脱敏堆栈和审计摘要，不包含 API Key、文件原文或完整模型输出。

## 安全边界

- 模型只能提出计划，工具白名单、风险等级和审批方式由本地代码决定。
- 普通只读操作可自动执行；外部影响操作按任务审批；敏感操作逐项审批。
- 审批令牌绑定计划、动作、参数摘要、有效期和单次使用次数。
- 支付、密码、验证码、绕过锁屏、静默安装和访问其他应用私有数据永久禁止。
- 模型接口必须使用 HTTPS；只有用户明确配置的本机回环接口可使用 HTTP。
- 外部网页工具只接受有效 HTTPS 地址。
- API Key 使用 Android Keystore 加密，不进入 Room、日志、诊断文件或 Git。

详细说明：

- [架构](docs/ARCHITECTURE.md)
- [安全模型](docs/SECURITY.md)
- [长期路线](docs/ROADMAP.md)
- [发布流程](docs/RELEASE.md)

## 当前边界

`v0.2.1` 仍是单 APK Native Core，不包含：

- AccessibilityService 跨应用自动操作
- Shizuku
- Shell、Python 或 Git Runtime
- 独立 Runtime APK
- Web 或桌面端
- 静默安装、支付自动化、root 或私有数据访问

这些形态只记录在后续路线中，不会通过降低 target SDK 或放宽审批策略提前加入。

## 构建

要求：

- JDK 17
- Android Platform 36
- Android Build Tools 35
- Gradle Wrapper 8.11.1
- Android Gradle Plugin 8.9.1

Windows：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-android.ps1 -ChinaMirrors
```

不传 `-ChinaMirrors` 时使用 Google Maven 与 Maven Central 官方源。镜像不会静默启用。

## 仓库结构

- `android-app/`：原生 Android 主应用。
- `termux-lite/`：已验证的兼容原型，不是主应用依赖。
- `docs/`：架构、安全、路线和发布说明。
- `.github/workflows/`：CI、模拟器测试和正式签名预发布。

项目采用 MIT License。GitHub Releases 是主发布渠道，首次安装和每次更新都保留 Android 系统确认。
