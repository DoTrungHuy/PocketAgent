#!/usr/bin/env python3
"""PocketAgent: a dependency-free, tablet-first AI agent runtime."""

from __future__ import print_function

import argparse
import getpass
import hashlib
import json
import os
import re
import shlex
import shutil
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, HTTPServer
from socketserver import ThreadingMixIn


APP_HOME = os.path.expanduser(os.environ.get("POCKETAGENT_HOME", "~/.pocketagent"))
APP_DIR = os.path.dirname(os.path.abspath(__file__))
SOURCE_ROOT = os.path.dirname(APP_DIR)
CONFIG_PATH = os.path.join(APP_HOME, "config.json")
SECRETS_PATH = os.path.join(APP_HOME, "secrets.env")
SESSIONS_DIR = os.path.join(APP_HOME, "sessions")
LOGS_DIR = os.path.join(APP_HOME, "logs")
PROVIDERS_PATH = (
    os.path.join(APP_DIR, "config", "providers.json")
    if os.path.isfile(os.path.join(APP_DIR, "config", "providers.json"))
    else os.path.join(SOURCE_ROOT, "config", "providers.json")
)
WEB_DIR = os.path.join(APP_DIR, "web")
VERSION_PATH = (
    os.path.join(APP_DIR, "VERSION")
    if os.path.isfile(os.path.join(APP_DIR, "VERSION"))
    else os.path.join(SOURCE_ROOT, "VERSION")
)
DEFAULT_WORKSPACE = os.path.expanduser("~/PocketAgentWorkspace")
MAX_FILE_BYTES = 1024 * 1024
MAX_TOOL_ROUNDS = 8
DEFAULT_TIMEOUT = 120


def ensure_dirs():
    for path in (APP_HOME, SESSIONS_DIR, LOGS_DIR):
        if not os.path.isdir(path):
            os.makedirs(path)


def load_json(path, default=None):
    try:
        with open(path, "r", encoding="utf-8") as handle:
            return json.load(handle)
    except (IOError, ValueError):
        return {} if default is None else default


def atomic_write_json(path, value, mode=None):
    parent = os.path.dirname(path)
    if parent and not os.path.isdir(parent):
        os.makedirs(parent)
    temp = path + ".tmp"
    with open(temp, "w", encoding="utf-8") as handle:
        json.dump(value, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    os.replace(temp, path)
    if mode is not None:
        try:
            os.chmod(path, mode)
        except OSError:
            pass


def load_providers():
    providers = load_json(PROVIDERS_PATH, {})
    if not providers:
        raise RuntimeError("provider 配置不存在或无法读取: " + PROVIDERS_PATH)
    return providers


def default_config():
    return {
        "provider": "",
        "endpoint": "",
        "model": "",
        "workspace": DEFAULT_WORKSPACE,
        "systemPrompt": (
            "你是运行在安卓平板上的 PocketAgent 助手。优先使用中文，回答简洁准确。"
            "你只能通过提供的工具访问指定工作目录，不能假装已经执行未执行的操作。"
            "修改文件前先理解现有内容；不要访问工作目录外的文件；不要泄露密钥。"
        ),
        "tools": {
            "enabled": True,
            "fileMode": "workspace",
            "shellEnabled": False,
            "shellAllowlist": [
                "pwd", "ls", "find", "grep", "cat", "head", "tail",
                "wc", "sort", "uniq", "date", "uname", "df", "du",
                "git"
            ]
        },
        "network": {
            "timeoutSeconds": DEFAULT_TIMEOUT
        },
        "web": {
            "host": "127.0.0.1",
            "port": 8765
        }
    }


def load_config():
    config = default_config()
    saved = load_json(CONFIG_PATH, {})
    merge_dict(config, saved)
    return config


def merge_dict(target, source):
    for key, value in source.items():
        if isinstance(value, dict) and isinstance(target.get(key), dict):
            merge_dict(target[key], value)
        else:
            target[key] = value


def load_secrets():
    result = {}
    try:
        with open(SECRETS_PATH, "r", encoding="utf-8") as handle:
            for raw in handle:
                line = raw.strip()
                if not line or line.startswith("#") or "=" not in line:
                    continue
                key, value = line.split("=", 1)
                result[key.strip()] = value.strip()
    except IOError:
        pass
    return result


def save_api_key(api_key):
    ensure_dirs()
    temp = SECRETS_PATH + ".tmp"
    with open(temp, "w", encoding="utf-8") as handle:
        handle.write("POCKETAGENT_API_KEY=" + api_key.replace("\n", "").replace("\r", "") + "\n")
    os.replace(temp, SECRETS_PATH)
    try:
        os.chmod(SECRETS_PATH, 0o600)
    except OSError:
        pass


def redact(text):
    secrets = load_secrets()
    result = str(text)
    for value in secrets.values():
        if value and len(value) >= 6:
            result = result.replace(value, "***REDACTED***")
    result = re.sub(r"\b(sk-[A-Za-z0-9_-]{8,})\b", "***REDACTED***", result)
    return result


def ask(prompt, default=None):
    suffix = " [" + str(default) + "]" if default not in (None, "") else ""
    value = input(prompt + suffix + ": ").strip()
    return value if value else (default or "")


def configure():
    ensure_dirs()
    providers = load_providers()
    keys = list(providers.keys())
    mainland = [key for key in keys if providers[key].get("mainland")]
    optional = [key for key in keys if not providers[key].get("mainland")]
    ordered = mainland + optional

    print("\nPocketAgent 模型配置（中国大陆服务优先）")
    print("API Key 只写入本机 %s，不写入 config.json。" % SECRETS_PATH)
    for index, key in enumerate(ordered, 1):
        marker = "" if providers[key].get("mainland") else " [可选]"
        print("%2d. %s%s" % (index, providers[key]["name"], marker))

    while True:
        choice = ask("选择服务商", "1")
        try:
            provider_key = ordered[int(choice) - 1]
            break
        except (ValueError, IndexError):
            print("请输入列表中的序号。")

    preset = providers[provider_key]
    endpoint = preset.get("endpoint", "")
    if not endpoint:
        endpoint = ask("完整 Chat Completions 接口地址（以 /chat/completions 结尾）")
    model = ask("模型名称（%s）" % preset.get("modelHint", ""), preset.get("defaultModel", ""))
    api_key = getpass.getpass("API Key（输入不会显示）: ").strip()
    if not endpoint.startswith("https://") and not endpoint.startswith("http://127.0.0.1"):
        raise ValueError("为保护密钥，仅允许 HTTPS 接口或本机 127.0.0.1。")
    if not model:
        raise ValueError("模型名称不能为空。")
    if not api_key:
        raise ValueError("API Key 不能为空。")

    config = load_config()
    config["provider"] = provider_key
    config["endpoint"] = endpoint.rstrip("/")
    config["model"] = model
    config["workspace"] = os.path.abspath(os.path.expanduser(
        ask("Agent 工作目录", config.get("workspace", DEFAULT_WORKSPACE))
    ))
    tools_answer = ask("是否启用文件工具？输入 no 可降级为纯聊天", "yes").lower()
    config["tools"]["enabled"] = tools_answer != "no"
    shell_answer = ask("是否启用受限命令工具？输入 yes 启用", "no").lower()
    config["tools"]["shellEnabled"] = config["tools"]["enabled"] and shell_answer == "yes"

    if not os.path.isdir(config["workspace"]):
        os.makedirs(config["workspace"])
    atomic_write_json(CONFIG_PATH, config, 0o600)
    save_api_key(api_key)
    print("\n配置完成。")
    print("工作目录: " + config["workspace"])
    print("命令工具: " + ("已启用（仅白名单）" if config["tools"]["shellEnabled"] else "默认关闭"))
    print("建议先运行: pocketagent doctor")


def safe_workspace_path(config, user_path):
    workspace = os.path.realpath(os.path.expanduser(config["workspace"]))
    candidate = os.path.realpath(os.path.join(workspace, user_path or "."))
    try:
        inside = os.path.commonpath([workspace, candidate]) == workspace
    except ValueError:
        inside = False
    if not inside:
        raise ValueError("拒绝访问工作目录以外的路径。")
    return candidate


def list_files(config, path=".", max_entries=200):
    target = safe_workspace_path(config, path)
    if not os.path.exists(target):
        return {"error": "路径不存在"}
    if os.path.isfile(target):
        return {"path": path, "type": "file", "size": os.path.getsize(target)}
    rows = []
    for name in sorted(os.listdir(target))[:int(max_entries)]:
        full = os.path.join(target, name)
        rows.append({
            "name": name,
            "type": "directory" if os.path.isdir(full) else "file",
            "size": None if os.path.isdir(full) else os.path.getsize(full)
        })
    return {"path": path, "entries": rows}


def read_file(config, path):
    target = safe_workspace_path(config, path)
    if not os.path.isfile(target):
        return {"error": "文件不存在"}
    size = os.path.getsize(target)
    if size > MAX_FILE_BYTES:
        return {"error": "文件超过 1MB，首版不直接读取", "size": size}
    with open(target, "r", encoding="utf-8", errors="replace") as handle:
        return {"path": path, "content": handle.read()}


def write_file(config, path, content):
    if config["tools"].get("fileMode") != "workspace":
        return {"error": "当前为只读模式"}
    target = safe_workspace_path(config, path)
    parent = os.path.dirname(target)
    if not os.path.isdir(parent):
        os.makedirs(parent)
    backup = None
    if os.path.exists(target):
        backup = target + ".pocketagent-backup-" + time.strftime("%Y%m%d-%H%M%S")
        shutil.copy2(target, backup)
    temp = target + ".pocketagent-tmp"
    with open(temp, "w", encoding="utf-8") as handle:
        handle.write(content)
    os.replace(temp, target)
    return {
        "path": path,
        "bytes": len(content.encode("utf-8")),
        "backup": os.path.basename(backup) if backup else None
    }


def run_command(config, command):
    if not config["tools"].get("shellEnabled"):
        return {"error": "命令工具未启用，请重新运行 pocketagent configure"}
    forbidden = (";", "&&", "||", "|", ">", "<", "`", "$(", "\n", "\r")
    if any(token in command for token in forbidden):
        return {"error": "命令包含管道、重定向或组合执行符，已拒绝"}
    try:
        args = shlex.split(command)
    except ValueError as exc:
        return {"error": "命令解析失败: " + str(exc)}
    if not args:
        return {"error": "命令为空"}
    allowed = config["tools"].get("shellAllowlist", [])
    if args[0] not in allowed:
        return {"error": "命令不在白名单中", "allowed": allowed}
    workspace = safe_workspace_path(config, ".")
    for arg in args[1:]:
        normalized = arg.replace("\\", "/")
        option_value = normalized.split("=", 1)[1] if "=" in normalized else ""
        if (
            os.path.isabs(arg)
            or re.match(r"^[A-Za-z]:", normalized)
            or ".." in normalized.split("/")
            or option_value.startswith("/")
            or re.match(r"^[A-Za-z]:", option_value)
        ):
            return {"error": "命令参数不能访问工作目录外的路径"}
        if not arg.startswith("-"):
            try:
                safe_workspace_path(config, arg)
            except ValueError:
                return {"error": "命令参数不能访问工作目录外的路径"}
    if args[0] == "git" and (len(args) < 2 or args[1] not in ("status", "diff", "log", "show")):
        return {"error": "git 仅允许 status、diff、log 和 show"}
    if args[0] == "find" and any(
        item in args for item in ("-exec", "-execdir", "-delete", "-ok", "-okdir", "-fprint")
    ):
        return {"error": "find 的执行、删除和写文件操作已禁用"}
    if args[0] == "sort" and any(item == "-o" or item.startswith("--output") for item in args):
        return {"error": "sort 写文件操作已禁用"}
    try:
        completed = subprocess.run(
            args,
            cwd=workspace,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            universal_newlines=True,
            timeout=30
        )
        output = completed.stdout or ""
        return {"exitCode": completed.returncode, "output": output[-12000:]}
    except (OSError, subprocess.TimeoutExpired) as exc:
        return {"error": str(exc)}


def tool_definitions(config):
    tools = [
        {
            "type": "function",
            "function": {
                "name": "list_files",
                "description": "列出 PocketAgent 工作目录内的文件。",
                "parameters": {
                    "type": "object",
                    "properties": {"path": {"type": "string"}},
                    "required": []
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "read_file",
                "description": "读取 PocketAgent 工作目录内不超过 1MB 的文本文件。",
                "parameters": {
                    "type": "object",
                    "properties": {"path": {"type": "string"}},
                    "required": ["path"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "write_file",
                "description": "写入 PocketAgent 工作目录内的文本文件；覆盖时自动备份。",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "path": {"type": "string"},
                        "content": {"type": "string"}
                    },
                    "required": ["path", "content"]
                }
            }
        }
    ]
    if config["tools"].get("shellEnabled"):
        tools.append({
            "type": "function",
            "function": {
                "name": "run_command",
                "description": "在工作目录运行单个白名单命令；不支持管道、重定向和组合命令。",
                "parameters": {
                    "type": "object",
                    "properties": {"command": {"type": "string"}},
                    "required": ["command"]
                }
            }
        })
    return tools


def execute_tool(config, name, arguments):
    try:
        if name == "list_files":
            return list_files(config, arguments.get("path", "."))
        if name == "read_file":
            return read_file(config, arguments["path"])
        if name == "write_file":
            return write_file(config, arguments["path"], arguments["content"])
        if name == "run_command":
            return run_command(config, arguments["command"])
        return {"error": "未知工具: " + name}
    except Exception as exc:
        return {"error": redact(str(exc))}


def normalize_content(content):
    if content is None:
        return ""
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts = []
        for item in content:
            if isinstance(item, str):
                parts.append(item)
            elif isinstance(item, dict):
                text = item.get("text") or item.get("content")
                if text:
                    parts.append(str(text))
        return "\n".join(parts)
    return str(content)


def validate_endpoint(endpoint):
    parsed = urllib.parse.urlparse(endpoint)
    if parsed.scheme == "https" and parsed.hostname:
        return
    if parsed.scheme == "http" and parsed.hostname in ("127.0.0.1", "localhost", "::1"):
        return
    raise ValueError("模型接口必须使用 HTTPS；仅本机 127.0.0.1/localhost 允许 HTTP。")


def open_url(request, timeout):
    hostname = urllib.parse.urlparse(request.full_url).hostname
    if hostname in ("127.0.0.1", "localhost", "::1"):
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        return opener.open(request, timeout=timeout)
    return urllib.request.urlopen(request, timeout=timeout)


def request_completion(config, messages):
    secrets = load_secrets()
    api_key = secrets.get("POCKETAGENT_API_KEY", "")
    if not api_key:
        raise RuntimeError("尚未配置 API Key，请运行 pocketagent configure。")
    if not config.get("endpoint") or not config.get("model"):
        raise RuntimeError("模型配置不完整，请运行 pocketagent configure。")
    validate_endpoint(config["endpoint"])

    payload = {
        "model": config["model"],
        "messages": messages
    }
    if config.get("tools", {}).get("enabled", True):
        payload["tools"] = tool_definitions(config)
        payload["tool_choice"] = "auto"
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        config["endpoint"],
        data=body,
        headers={
            "Authorization": "Bearer " + api_key,
            "Content-Type": "application/json",
            "User-Agent": "PocketAgent/0.1"
        },
        method="POST"
    )
    timeout = int(config.get("network", {}).get("timeoutSeconds", DEFAULT_TIMEOUT))
    try:
        with open_url(request, timeout=timeout) as response:
            raw = response.read().decode("utf-8", errors="replace")
            data = json.loads(raw)
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError("模型接口 HTTP %s: %s" % (exc.code, redact(raw[:1000])))
    except urllib.error.URLError as exc:
        raise RuntimeError("无法连接模型接口: " + redact(str(exc.reason)))
    except ValueError:
        raise RuntimeError("模型接口返回了无法解析的 JSON。")
    try:
        return data["choices"][0]["message"]
    except (KeyError, IndexError, TypeError):
        raise RuntimeError("模型接口响应缺少 choices[0].message: " + redact(str(data)[:1000]))


def session_path(session_id):
    safe_id = re.sub(r"[^A-Za-z0-9_.-]", "_", session_id or "default")[:80]
    return os.path.join(SESSIONS_DIR, safe_id + ".json")


def load_session(session_id):
    return load_json(session_path(session_id), [])


def save_session(session_id, messages):
    atomic_write_json(session_path(session_id), messages[-80:], 0o600)


def chat_once(user_message, session_id="default"):
    ensure_dirs()
    config = load_config()
    messages = load_session(session_id)
    if not messages:
        messages = [{"role": "system", "content": config["systemPrompt"]}]
    messages.append({"role": "user", "content": user_message})
    events = []

    for _round in range(MAX_TOOL_ROUNDS):
        assistant = request_completion(config, messages)
        tool_calls = assistant.get("tool_calls") or []
        messages.append(assistant)
        if not tool_calls:
            content = normalize_content(assistant.get("content"))
            save_session(session_id, messages)
            return content, events
        for call in tool_calls:
            function = call.get("function", {})
            name = function.get("name", "")
            raw_args = function.get("arguments") or "{}"
            if isinstance(raw_args, dict):
                arguments = raw_args
            else:
                try:
                    arguments = json.loads(raw_args)
                except (ValueError, TypeError):
                    arguments = None
            if arguments is None:
                result = {"error": "工具参数不是有效 JSON"}
            else:
                result = execute_tool(config, name, arguments)
            safe_result = redact(json.dumps(result, ensure_ascii=False))
            events.append({"tool": name, "result": result})
            messages.append({
                "role": "tool",
                "tool_call_id": call.get("id", ""),
                "content": safe_result
            })
    raise RuntimeError("工具调用超过 %d 轮，已停止以避免循环。" % MAX_TOOL_ROUNDS)


def interactive_chat():
    print("PocketAgent 终端模式。输入 /exit 退出，/reset 清空当前会话。")
    while True:
        try:
            message = input("\n你> ").strip()
        except (EOFError, KeyboardInterrupt):
            print("")
            return
        if not message:
            continue
        if message == "/exit":
            return
        if message == "/reset":
            reset_session("cli")
            print("会话已清空。")
            continue
        try:
            reply, events = chat_once(message, "cli")
            for event in events:
                print("[工具] " + event["tool"])
            print("\nPocketAgent> " + reply)
        except Exception as exc:
            print("错误: " + redact(str(exc)))


def reset_session(session_id):
    path = session_path(session_id)
    try:
        os.remove(path)
    except OSError:
        pass


def check_url(url, timeout=8, method="HEAD"):
    try:
        request = urllib.request.Request(url, method=method, headers={"User-Agent": "PocketAgent/0.1"})
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return True, "HTTP " + str(response.status)
    except urllib.error.HTTPError as exc:
        return exc.code < 500, "HTTP " + str(exc.code)
    except Exception as exc:
        return False, redact(str(exc))


def doctor(test_api=False):
    ensure_dirs()
    config = load_config()
    checks = []

    def add(name, ok, detail):
        checks.append((name, ok, detail))
        print(("[OK]   " if ok else "[FAIL] ") + name + " - " + detail)

    add("Python", sys.version_info >= (3, 9), sys.version.split()[0])
    add("配置文件", os.path.isfile(CONFIG_PATH), CONFIG_PATH)
    add("密钥文件", bool(load_secrets().get("POCKETAGENT_API_KEY")), SECRETS_PATH)
    workspace = os.path.expanduser(config.get("workspace", DEFAULT_WORKSPACE))
    add("工作目录", os.path.isdir(workspace), workspace)
    add("Gateway 监听", config.get("web", {}).get("host") == "127.0.0.1",
        config.get("web", {}).get("host", "未设置"))

    for name, url in (
        ("DeepSeek 网络", "https://api.deepseek.com"),
        ("阿里云百炼网络", "https://dashscope.aliyuncs.com"),
        ("GitHub 网络（仅更新需要）", "https://github.com")
    ):
        ok, detail = check_url(url)
        add(name, ok, detail)

    if config.get("endpoint"):
        endpoint_root = re.match(r"^(https?://[^/]+)", config["endpoint"])
        if endpoint_root:
            ok, detail = check_url(endpoint_root.group(1))
            add("当前模型服务网络", ok, detail)
    else:
        add("当前模型配置", False, "尚未运行 pocketagent configure")

    if test_api:
        try:
            reply, _events = chat_once("只回复 POCKETAGENT_OK，不要调用工具。", "doctor-api")
            add("模型 API 实测", "POCKETAGENT_OK" in reply.upper(), redact(reply[:120]))
        except Exception as exc:
            add("模型 API 实测", False, redact(str(exc)))

    failed = len([item for item in checks if not item[1]])
    print("\n诊断结果: %d 项通过，%d 项失败。" % (len(checks) - failed, failed))
    return 1 if failed else 0


def capture_command(args, timeout=10):
    try:
        completed = subprocess.run(
            args,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            universal_newlines=True,
            timeout=timeout
        )
        return redact(completed.stdout.strip()) or "(无输出)"
    except (OSError, subprocess.TimeoutExpired) as exc:
        return "不可用: " + redact(str(exc))


def safe_config_for_report(config):
    return {
        "provider": config.get("provider", ""),
        "endpointHost": urllib.parse.urlparse(config.get("endpoint", "")).hostname or "",
        "model": config.get("model", ""),
        "workspace": config.get("workspace", ""),
        "tools": {
            "enabled": config.get("tools", {}).get("enabled", True),
            "fileMode": config.get("tools", {}).get("fileMode", ""),
            "shellEnabled": config.get("tools", {}).get("shellEnabled", False),
            "shellAllowlist": config.get("tools", {}).get("shellAllowlist", [])
        },
        "web": config.get("web", {})
    }


def generate_report(test_api=False, output_path=None):
    ensure_dirs()
    config = load_config()
    timestamp = time.strftime("%Y%m%d-%H%M%S")
    if output_path:
        report_path = os.path.abspath(os.path.expanduser(output_path))
    else:
        report_path = os.path.expanduser("~/PocketAgentReport-%s.txt" % timestamp)

    doctor_lines = []
    original_stdout = sys.stdout

    class ReportWriter:
        def write(self, value):
            doctor_lines.append(value)

        def flush(self):
            return

    try:
        sys.stdout = ReportWriter()
        doctor_exit = doctor(test_api)
    finally:
        sys.stdout = original_stdout

    log_path = os.path.join(LOGS_DIR, "gateway.log")
    try:
        with open(log_path, "r", encoding="utf-8", errors="replace") as handle:
            log_tail = "".join(handle.readlines()[-120:])
    except IOError:
        log_tail = "(暂无 gateway.log)"

    device_props = {}
    for prop in (
        "ro.product.manufacturer",
        "ro.product.model",
        "ro.product.device",
        "ro.build.version.release",
        "ro.build.version.sdk",
        "ro.build.version.incremental"
    ):
        value = capture_command(["getprop", prop])
        if not value.startswith("不可用"):
            device_props[prop] = value

    sections = [
        "# PocketAgent 实机诊断报告",
        "",
        "生成时间: %s" % time.strftime("%Y-%m-%d %H:%M:%S %z"),
        "报告版本: 1",
        "主机名摘要: %s" % hashlib.sha256(socket.gethostname().encode("utf-8")).hexdigest()[:12],
        "",
        "## PocketAgent",
        "版本: %s" % capture_command([sys.executable, os.path.abspath(__file__), "version"]),
        "Python: %s" % sys.version.replace("\n", " "),
        "程序目录: %s" % APP_DIR,
        "数据目录: %s" % APP_HOME,
        "",
        "## 设备（不采集序列号和 Android ID）",
        json.dumps(device_props, ensure_ascii=False, indent=2),
        "",
        "uname -a:",
        capture_command(["uname", "-a"]),
        "",
        "Termux 信息:",
        capture_command(["termux-info"], timeout=20),
        "",
        "存储信息:",
        capture_command(["df", "-h", os.path.expanduser("~")]),
        "",
        "## 脱敏配置",
        json.dumps(safe_config_for_report(config), ensure_ascii=False, indent=2),
        "密钥文件存在: %s" % ("是" if load_secrets().get("POCKETAGENT_API_KEY") else "否"),
        "",
        "## Doctor",
        redact("".join(doctor_lines).strip()),
        "Doctor exit code: %d" % doctor_exit,
        "API 实测: %s" % ("已执行" if test_api else "未执行（未产生模型调用）"),
        "",
        "## 进程",
        capture_command(["ps", "-A"]),
        "",
        "## Gateway 日志末尾（已脱敏）",
        redact(log_tail),
        "",
        "## 用户补充",
        "安装是否成功: ",
        "Web UI 是否打开: ",
        "连续三轮对话是否成功: ",
        "文件列出/写入/读取是否成功: ",
        "熄屏 5 分钟后是否仍运行: ",
        "系统省电模式状态: ",
        "看到的其他错误: ",
        ""
    ]
    parent = os.path.dirname(report_path)
    if parent and not os.path.isdir(parent):
        os.makedirs(parent)
    with open(report_path, "w", encoding="utf-8") as handle:
        handle.write("\n".join(sections))
    try:
        os.chmod(report_path, 0o600)
    except OSError:
        pass
    print("报告已生成: " + report_path)
    print("发送前可以打开检查；报告不包含 API Key、设备序列号或 Android ID。")
    return report_path


class ThreadedHTTPServer(ThreadingMixIn, HTTPServer):
    daemon_threads = True


class PocketAgentHandler(BaseHTTPRequestHandler):
    server_version = "PocketAgent/0.1"

    def log_message(self, format_string, *args):
        sys.stderr.write("%s - %s\n" % (self.address_string(), format_string % args))

    def send_json(self, value, status=200):
        body = json.dumps(value, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/api/status":
            config = load_config()
            self.send_json({
                "ok": True,
                "provider": config.get("provider"),
                "model": config.get("model"),
                "workspace": config.get("workspace"),
                "toolsEnabled": config.get("tools", {}).get("enabled", True),
                "shellEnabled": config.get("tools", {}).get("shellEnabled", False)
            })
            return
        if self.path in ("/", "/index.html"):
            path = os.path.join(WEB_DIR, "index.html")
            try:
                with open(path, "rb") as handle:
                    body = handle.read()
            except IOError:
                self.send_error(404)
                return
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(body)
            return
        self.send_error(404)

    def do_POST(self):
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length > 1024 * 1024:
                self.send_json({"ok": False, "error": "请求过大"}, 413)
                return
            data = json.loads(self.rfile.read(length).decode("utf-8"))
            if self.path == "/api/chat":
                message = str(data.get("message", "")).strip()
                if not message:
                    self.send_json({"ok": False, "error": "消息不能为空"}, 400)
                    return
                reply, events = chat_once(message, str(data.get("session", "web")))
                self.send_json({"ok": True, "reply": reply, "events": events})
                return
            if self.path == "/api/reset":
                reset_session(str(data.get("session", "web")))
                self.send_json({"ok": True})
                return
            self.send_error(404)
        except Exception as exc:
            sys.stderr.write("PocketAgent request error: %s\n" % redact(str(exc)))
            self.send_json({"ok": False, "error": redact(str(exc))}, 500)


def serve():
    config = load_config()
    host = config.get("web", {}).get("host", "127.0.0.1")
    port = int(config.get("web", {}).get("port", 8765))
    if host != "127.0.0.1":
        raise RuntimeError("首版为安全起见只允许监听 127.0.0.1。")
    server = ThreadedHTTPServer((host, port), PocketAgentHandler)
    print("PocketAgent Web UI: http://%s:%d" % (host, port))
    server.serve_forever()


def print_version():
    try:
        with open(VERSION_PATH, "r", encoding="utf-8") as handle:
            print(handle.read().strip())
    except IOError:
        print("0.1.0-dev")


def main(argv=None):
    parser = argparse.ArgumentParser(description="PocketAgent 平板原生轻量 AI Agent")
    sub = parser.add_subparsers(dest="command")
    sub.add_parser("configure", help="配置模型、工作目录与工具权限")
    sub.add_parser("chat", help="启动终端对话")
    sub.add_parser("serve", help="启动本机 Web UI")
    doctor_parser = sub.add_parser("doctor", help="检查环境和网络")
    doctor_parser.add_argument("--test-api", action="store_true", help="实际调用一次模型 API")
    report_parser = sub.add_parser("report", help="生成脱敏实机诊断报告")
    report_parser.add_argument("--test-api", action="store_true", help="报告中包含一次真实模型测试")
    report_parser.add_argument("--output", help="自定义报告输出路径")
    sub.add_parser("version", help="显示版本")
    args = parser.parse_args(argv)

    if args.command == "configure":
        configure()
        return 0
    if args.command == "chat":
        interactive_chat()
        return 0
    if args.command == "serve":
        serve()
        return 0
    if args.command == "doctor":
        return doctor(args.test_api)
    if args.command == "report":
        generate_report(args.test_api, args.output)
        return 0
    if args.command == "version":
        print_version()
        return 0
    parser.print_help()
    return 0


if __name__ == "__main__":
    sys.exit(main())
