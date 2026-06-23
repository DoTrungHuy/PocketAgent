# PocketAgent 实机测试清单

首轮目标设备：

- 小米平板 7S Pro
- 联想小新 Pad 2024

## 设备信息

每台设备测试前记录：

- 完整型号：
- Android / HyperOS / ZUI 版本：
- CPU 架构：
- 可用存储：
- Termux 版本和安装来源：
- 网络类型：
- 测试日期：

## 全新安装

1. 从可信来源安装 Termux。
2. 把完整 PocketAgent 源码目录传到平板。
3. 在目录内运行 `bash install.sh`。
4. 确认 PocketAgent 安装过程没有新增或调用 PRoot、Ubuntu、Node.js 或 Google 服务；设备原本安装这些软件不算失败，但必须记录。
5. 记录安装耗时、下载量和最终占用空间。
6. 再次运行 `bash install.sh --no-config`，确认不会破坏已有配置。

## 模型与工具

1. 至少配置一个中国大陆模型服务。
2. 运行 `pocketagent doctor --test-api`。
3. 使用 `pocketagent chat` 完成连续三轮对话。
4. 让 Agent 列出工作目录。
5. 让 Agent 新建文本文件并读取回来。
6. 覆盖该文件，确认生成备份。
7. 尝试读取 `../` 路径，确认被拒绝。
8. 默认配置下尝试执行命令，确认命令工具不可用。
9. 显式启用受限命令后，确认白名单命令可用、管道和重定向被拒绝。

## Web UI 与后台

1. 运行 `pocketagent start`。
2. 在平板浏览器打开 `http://127.0.0.1:8765`。
3. 完成对话和文件工具调用。
4. 熄屏五分钟后检查进程是否仍存活。
5. 分别测试系统省电模式开启和关闭时的表现。
6. 运行 `pocketagent stop`，确认端口关闭。

## 故障模拟

- 断网后运行 `pocketagent doctor`。
- 使用错误 API Key 运行 `pocketagent doctor --test-api`。
- 填写不存在的模型名称。
- 让模型服务返回 401、404、429 和 500。
- 删除配置文件但保留密钥文件。
- 删除密钥文件但保留配置文件。
- 将工作目录设为只读。
- 在磁盘空间不足时尝试写文件。

## 卸载

1. 运行 `pocketagent uninstall`。
2. 确认程序和命令入口已删除。
3. 确认配置、会话、密钥和工作目录默认保留。
4. 手动备份后再测试完全清理。

## 通过标准

- 不依赖 Google 服务、Node.js、Docker 或 Linux 容器。
- 至少一个中国大陆模型能够完成对话和工具调用。
- 工作目录边界不可绕过。
- API Key 不出现在配置、日志和网页响应中。
- 两台设备的后台存活问题都有准确记录。

## 回传报告

完成测试后运行：

```bash
pocketagent report --test-api
```

把生成的 `PocketAgentReport-日期时间.txt` 文件连同以下内容发给开发者：

- 哪一台设备；
- 哪一步失败；
- 失败前最后一个操作；
- 必要时附一张错误画面截图。

不要发送 `~/.pocketagent/secrets.env`。
