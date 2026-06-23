# PocketAgent 实机操作与回传指南

本指南用于小米平板 7S Pro 和联想小新 Pad 2024。建议先只测试一台，修正问题后再测试第二台。

## 一、把项目传到平板

当前还没有 GitHub 发布包，先把电脑上的整个目录：

```text
D:\PocketAgent部署
```

复制到平板的“下载”目录。不要只复制 `install.sh`，必须保留 `app`、`config`、`docs` 等子目录。

可以使用 USB、局域网传输或可信网盘。不要把 API Key 放进项目目录。

## 二、安装 Termux

1. 从 F-Droid 或 Termux 官方可信渠道安装最新版 Termux。
2. 不要使用 Google Play 中长期未更新的旧版 Termux。
3. 首次打开 Termux 后执行：

```bash
termux-setup-storage
```

Android 弹出权限申请时，允许 Termux 访问共享文件。

## 三、找到项目并安装

一般下载目录对应：

```bash
cd ~/storage/downloads/PocketAgent部署
```

如果你给目录改成了英文名，则使用实际目录名，例如：

```bash
cd ~/storage/downloads/PocketAgent
```

先确认文件完整：

```bash
ls
```

应该至少看到：

```text
app  config  docs  tests  install.sh  pocketagent  README.md
```

开始安装：

```bash
bash install.sh
```

安装器会安装 Termux Python、curl 和证书，然后进入模型配置。

## 四、配置模型

建议第一次使用你已经拥有 API Key 的中国大陆服务。

配置时依次选择：

1. 服务商；
2. 模型名称；
3. API Key；
4. Agent 工作目录；
5. 是否启用文件工具；
6. 是否启用命令工具。

第一次测试建议：

- 文件工具：`yes`
- 命令工具：`no`
- 工作目录：直接回车，使用默认值

API Key 输入时终端不会显示字符，这是正常现象。

## 五、先做基础诊断

```bash
pocketagent doctor
```

这一步不调用模型，不产生模型费用。将所有 `[FAIL]` 行记录下来。

然后执行一次真实 API 测试：

```bash
pocketagent doctor --test-api
```

预期出现：

```text
[OK] 模型 API 实测
```

如果失败，不要反复重试。先生成报告发回分析。

## 六、测试终端 Agent

```bash
pocketagent chat
```

依次发送以下内容：

```text
你好，请只回复当前模型名称。
```

```text
请列出你的工作目录。
```

```text
请在工作目录创建 test-pocketagent.txt，写入“PocketAgent 实机测试成功”，然后读取并告诉我内容。
```

确认三轮都成功后输入：

```text
/exit
```

## 七、测试平板 Web UI

启动后台服务：

```bash
pocketagent start
```

查看状态：

```bash
pocketagent status
```

打开平板浏览器，访问：

```text
http://127.0.0.1:8765
```

在网页中发送一条消息。随后锁屏或熄屏 5 分钟，再回到网页发送第二条消息。

如果第二条消息失败，执行：

```bash
pocketagent status
pocketagent logs
```

并记录平板是否开启了省电模式、是否允许 Termux 后台运行。

## 八、生成回传报告

普通报告，不调用模型：

```bash
pocketagent report
```

完成模型测试后推荐生成：

```bash
pocketagent report --test-api
```

报告默认位于 Termux 主目录，文件名类似：

```text
PocketAgentReport-20260610-103000.txt
```

为了方便在文件管理器中找到，可以复制到下载目录：

```bash
cp ~/PocketAgentReport-*.txt ~/storage/downloads/
```

报告不会主动收集 API Key、设备序列号或 Android ID。发送前仍请用下面命令查看：

```bash
cat ~/PocketAgentReport-*.txt
```

绝对不要发送：

```text
~/.pocketagent/secrets.env
```

## 九、发回哪些内容

每台平板分别发回：

1. `PocketAgentReport-日期时间.txt`
2. 设备名称和系统版本
3. 失败发生在哪一步
4. 失败前执行的最后一条命令或最后一次操作
5. 有界面错误时附一张截图

建议先发小米平板 7S Pro 的结果。修正并更新源码后，再测试联想小新 Pad 2024，避免两台设备重复遇到同一个基础问题。
