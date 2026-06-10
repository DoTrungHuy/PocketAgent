# AgentPad

AgentPad 是一个从安卓平板实际能力出发设计的轻量 AI Agent，而不是某个桌面 Agent 的安卓搬运版。

首版直接运行在 Termux 的 Python 环境中，不需要 root、PRoot Ubuntu、Docker、Node.js 或 Google 服务。它通过中国大陆可访问的大模型 API 提供对话、工作目录文件操作、本地网页界面和可选的受限命令工具。

> 当前状态：`0.1.0-dev` 本地原型。小米平板 7S Pro 和联想小新 Pad 2024 均已完成首轮核心链路验证，但完整故障与后台存活验收尚未完成，也尚未发布到 GitHub。

## 为什么采用这个方向

完整 Agent 框架在桌面和服务器上功能丰富，但放到安卓平板后经常需要 Linux 容器、Node.js、原生模块或大量 Python 依赖。AgentPad 首版只保留平板真正需要的部分：

- 中国大陆网络和模型服务优先；
- Termux 原生运行，安装体积和故障面较小；
- 本机浏览器即可使用，不需要额外 App；
- 文件工具被限制在专用工作目录；
- 命令执行默认关闭，启用后也只允许单个白名单命令；
- 密钥与普通配置分离；
- 不依赖 Google Play、Google 登录或 Google Services。

OpenClaw、nanobot 等框架未来可以作为高级后端，但不是 AgentPad 的运行前提。

## 当前能力

- OpenAI-compatible Chat Completions Agent 循环；
- 模型原生 function calling 工具调用；
- 列出、读取和写入工作目录文件；
- 覆盖文件时自动创建备份；
- 可选受限命令执行；
- 对不支持 function calling 的接口可关闭工具，降级为纯聊天；
- 终端对话模式；
- 仅监听 `127.0.0.1` 的移动端 Web UI；
- 会话持久化；
- 网络、配置、权限和模型 API 诊断；
- 后台启动、停止、状态和日志命令；
- 零第三方 Python 包依赖。

## 支持的模型配置

首版预置：

- DeepSeek
- 阿里云百炼 / 通义千问
- 智谱 GLM
- Kimi / Moonshot
- MiniMax 中国大陆
- 硅基流动
- 火山方舟 / 豆包
- 百度智能云千帆
- 自定义 OpenAI-compatible 接口

OpenAI 和 Anthropic-compatible 接口只作为可选项，不是中国大陆环境的验收依赖。

模型名称和接口能力可能由服务商调整。预置值只是安装时的合理默认值，正式发布前必须使用真实账号逐项验证。

## 本地原型安装

1. 从 F-Droid 或 Termux 官方可信渠道安装 Termux。
2. 将完整 AgentPad 源码目录传到平板。
3. 在 Termux 中进入该目录并执行：

```bash
bash install.sh
```

安装器只安装 Termux 的 `python`、`curl` 和 CA 证书，不安装 Linux 容器或 Node.js。

配置完成后：

```bash
agentpad doctor --test-api
agentpad start
```

然后在平板浏览器打开：

```text
http://127.0.0.1:8765
```

## 命令

```text
agentpad configure          配置模型、工作目录和工具权限
agentpad doctor             检查环境与网络
agentpad doctor --test-api  真实调用一次模型 API
agentpad report             生成不调用模型的脱敏实机报告
agentpad report --test-api  报告中加入一次真实模型测试
agentpad chat               Termux 终端对话
agentpad start              后台启动 Web UI
agentpad stop               停止 Web UI
agentpad restart            重启 Web UI
agentpad status             查看状态
agentpad logs               查看日志
agentpad update             更新程序
agentpad uninstall          卸载程序并保留用户数据
agentpad uninstall --purge  二次确认后清除配置、密钥、会话和日志
```

## 数据位置

```text
~/.agentpad/config.json       非敏感配置
~/.agentpad/secrets.env       API Key，权限 600
~/.agentpad/sessions/         本地会话
~/.agentpad/logs/             运行日志
~/AgentPadWorkspace/          Agent 默认工作目录
```

API Key 不会写入 `config.json`。程序在错误、网页响应和常见日志中会尝试脱敏，但仍不应把整个 `~/.agentpad` 目录上传或分享。

## 安全边界

- Web UI 固定监听 `127.0.0.1`，首版不支持公网或局域网暴露。
- 路径经过真实路径解析，工具不能访问工作目录外的文件。
- 文件覆盖前会在同一目录创建带时间戳的备份。
- 命令工具默认关闭。
- 命令工具不支持管道、重定向、组合命令、绝对路径和上级目录。
- 不自动安装技能、插件或来源不明的软件包。
- 不提供代理、翻墙或绕过地区限制的功能。

Agent 仍然可能生成错误内容或误判任务。不要把支付、邮箱、网盘、身份资料等高风险数据放进工作目录。

## 中国大陆网络策略

AgentPad 的轻量核心不需要 npm、PyPI 或 Ubuntu 软件源，因此避开了三类常见安装故障。

- Termux 软件包仍通过用户当前配置的软件源安装。
- 模型请求直接访问用户选择的中国大陆服务商。
- GitHub 只在下载或更新 AgentPad 时需要，不影响日常运行。
- 正式发布包将提供 SHA256 校验，并允许配置可信的备用发布地址。
- 不会静默替换软件源，也不会自动信任第三方镜像。

## 实机验证

第一轮目标设备：

- 小米平板 7S Pro
- 联想小新 Pad 2024

完整步骤见 [docs/DEVICE_TEST_CHECKLIST.md](docs/DEVICE_TEST_CHECKLIST.md)，已验证结果见 [docs/DEVICE_TEST_RESULTS.md](docs/DEVICE_TEST_RESULTS.md)。只有完成真实安装、国内模型工具调用、后台存活和故障恢复测试后，项目才适合创建公开仓库并标记首个版本。

给开发者回传结果时，运行：

```bash
agentpad report --test-api
```

报告默认生成在 Termux 主目录，文件名类似 `AgentPadReport-20260610-103000.txt`。它不会采集 API Key、设备序列号或 Android ID。发送前仍建议自己打开检查一次。

## 已知限制

- 对话支持 OpenAI-compatible Chat Completions；文件 Agent 工具还要求模型支持 function calling。
- Web UI 暂不流式输出。
- 首版不提供语音、图片、微信控制、无障碍自动化或 Android 系统级操作。
- Android 厂商的后台限制可能终止 Termux 进程，需要逐台设备验证。
- `agentpad update` 在公开发布前只支持重新安装本地源码。

## 开发检查

项目测试只使用 Python 标准库：

```bash
python -m unittest discover -s tests -v
```

Shell 脚本建议使用 ShellCheck：

```bash
shellcheck bootstrap.sh install.sh agentpad
```

## 上游参考

- [Termux](https://termux.dev/)
- [OpenClaw](https://github.com/openclaw/openclaw)
- [nanobot](https://github.com/HKUDS/nanobot)

AgentPad 不复制这些项目的内核；它们用于比较功能边界和后续可选后端。
