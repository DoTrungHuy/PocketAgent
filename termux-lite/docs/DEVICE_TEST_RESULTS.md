# PocketAgent 实机测试结果

本文只记录报告和日志能够证明的结果。用户主观确认但报告中没有证据的项目，继续列为待验证。

## 小米平板 7S Pro：首轮核心链路通过

测试时间：2026-06-09 23:49（中国标准时间）

设备环境：

- 型号标识：`25053RP5CC`
- 设备代号：`violin`
- 系统：Android 16，HyperOS `OS3.0.303.0.WOTCNXM`
- 架构：ARM64
- Termux：`0.119.0-beta.3`，GitHub 发布版
- Termux 软件源：北京大学镜像
- Python：`3.13.13`
- 可用存储：约 149 GB

已通过：

- `install.sh` 完成安装，没有出现 Traceback、ERROR 或失败信息。
- PocketAgent `0.1.0-dev` 正确安装到 Termux 用户目录。
- 安装器只处理 Python、curl 和 CA 证书，没有调用 Google 服务。
- DeepSeek 配置成功，请求模型名为 `deepseek-v4-flash`。
- `pocketagent doctor --test-api` 共 10 项通过、0 项失败。
- DeepSeek 真实 API 调用返回 `POCKETAGENT_OK`。
- Web UI 仅监听 `127.0.0.1:8765`。
- Web 首页和状态接口返回 HTTP 200。
- 网页对话接口连续出现 3 次 HTTP 200。
- GitHub、DeepSeek 和阿里云百炼网络端点均可达。
- 提供的安装日志和诊断报告中未发现 API Key。

需要准确说明：

- 这不是纯净 Termux 环境。设备测试前已经存在 Node.js、npm、PRoot、proot-distro 和其他软件包。
- 安装日志表明 PocketAgent 没有依赖或安装上述组件，因此轻量运行路线得到验证；但“空环境首次安装”仍需另测。
- DeepSeek 和当前模型服务的 HTTP 401 是未携带认证信息的网络探测结果，表示端点可达；后续带密钥的真实 API 测试已经成功。
- 阿里云百炼的 HTTP 404 同样来自根地址网络探测，不代表当前 DeepSeek 配置失败。
- `favicon.ico` 的 HTTP 404 只表示项目暂未提供网页图标，不影响 Web UI。

尚待验证：

- 终端模式连续三轮对话的人工确认。
- 文件列出、创建、读取、覆盖备份和越界拒绝。
- 默认命令工具关闭，以及启用后的白名单限制。
- 熄屏五分钟后的后台存活。
- 系统省电模式开启和关闭的差异。
- `stop`、`restart`、重复安装和失败恢复。
- 断网、错误 API Key、错误模型名和服务端异常诊断。
- 卸载保留数据和完全清理。
- 纯净 Termux 环境安装。

## 联想小新 Pad 2024

### 首轮核心链路通过

测试时间：2026-06-10 00:19（中国标准时间）

设备环境：

- 型号标识：`TB331FC`
- 系统：Android 15，ZUI `17.0.680`
- 架构：ARM64，同时支持 32 位 ABI
- Termux：`0.119.0-beta.3`，F-Droid 发布版
- Termux 软件源：中国科学技术大学镜像
- Python：`3.13.13`
- 可用存储：约 73 GB

已通过：

- PocketAgent `0.1.0-dev` 能够正常运行。
- DeepSeek 配置成功，请求模型名为 `deepseek-v4-flash`。
- `pocketagent doctor --test-api` 共 10 项通过、0 项失败。
- DeepSeek 真实 API 调用返回 `POCKETAGENT_OK`。
- Web UI 仅监听 `127.0.0.1:8765`。
- Web 首页、状态接口和网页对话接口均返回 HTTP 200。
- 文件工具已开启。
- 受限命令工具已开启，并使用预设白名单。
- 用户人工确认聊天和当前功能可以正常使用。
- 提供的诊断报告中未发现 API Key。

需要准确说明：

- 报告能够证明受限命令工具已开启，但没有记录具体命令工具调用结果。
- `favicon.ico` 的 HTTP 404 不影响 Web UI。
- 网络探测中的 HTTP 401 和 404 不代表模型调用失败；带密钥的真实 API 测试已经成功。

尚待验证：

- 文件创建、读取、覆盖备份和越界拒绝的逐项结果。
- 白名单命令成功执行，以及管道、重定向和组合命令被拒绝。
- 熄屏五分钟后的后台存活。
- 系统省电模式开启和关闭的差异。
- `stop`、`restart`、重复安装和失败恢复。
- 故障模拟和卸载流程。
