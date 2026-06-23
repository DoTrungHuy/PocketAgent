import importlib.util
import json
import os
import shutil
import stat
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer
from socketserver import ThreadingMixIn


ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODULE_PATH = os.path.join(ROOT, "app", "pocketagent.py")
SPEC = importlib.util.spec_from_file_location("pocketagent_module", MODULE_PATH)
pocketagent = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(pocketagent)


class PocketAgentTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.mkdtemp()
        self.home = os.path.join(self.temp, ".pocketagent")
        self.workspace = os.path.join(self.temp, "workspace")
        os.makedirs(self.workspace)
        pocketagent.APP_HOME = self.home
        pocketagent.CONFIG_PATH = os.path.join(self.home, "config.json")
        pocketagent.SECRETS_PATH = os.path.join(self.home, "secrets.env")
        pocketagent.SESSIONS_DIR = os.path.join(self.home, "sessions")
        pocketagent.LOGS_DIR = os.path.join(self.home, "logs")
        self.config = pocketagent.default_config()
        self.config["workspace"] = self.workspace

    def tearDown(self):
        shutil.rmtree(self.temp)

    def test_workspace_blocks_parent_escape(self):
        with self.assertRaises(ValueError):
            pocketagent.safe_workspace_path(self.config, "../secret.txt")

    def test_write_creates_backup(self):
        path = os.path.join(self.workspace, "note.txt")
        with open(path, "w", encoding="utf-8") as handle:
            handle.write("old")
        result = pocketagent.write_file(self.config, "note.txt", "new")
        self.assertTrue(result["backup"])
        with open(path, "r", encoding="utf-8") as handle:
            self.assertEqual(handle.read(), "new")

    def test_shell_is_disabled_by_default(self):
        result = pocketagent.run_command(self.config, "ls")
        self.assertIn("未启用", result["error"])

    def test_shell_rejects_composed_command(self):
        self.config["tools"]["shellEnabled"] = True
        result = pocketagent.run_command(self.config, "ls | cat")
        self.assertIn("组合", result["error"])

    def test_shell_restricts_git_subcommands(self):
        self.config["tools"]["shellEnabled"] = True
        result = pocketagent.run_command(self.config, "git clean -fd")
        self.assertIn("git 仅允许", result["error"])

    def test_shell_restricts_find_exec(self):
        self.config["tools"]["shellEnabled"] = True
        result = pocketagent.run_command(self.config, "find . -exec cat {} ;")
        self.assertIn("组合", result["error"])

    def test_secret_redaction(self):
        os.makedirs(self.home)
        with open(pocketagent.SECRETS_PATH, "w", encoding="utf-8") as handle:
            handle.write("POCKETAGENT_API_KEY=sk-super-secret-value\n")
        self.assertNotIn("sk-super-secret-value", pocketagent.redact("bad sk-super-secret-value"))

    def test_api_key_file_permissions(self):
        pocketagent.save_api_key("secret-value")
        if os.name != "nt":
            mode = stat.S_IMODE(os.stat(pocketagent.SECRETS_PATH).st_mode)
            self.assertEqual(mode, 0o600)

    def test_provider_presets_are_valid(self):
        providers = pocketagent.load_providers()
        self.assertIn("deepseek", providers)
        self.assertIn("dashscope", providers)
        for key, provider in providers.items():
            if provider["endpoint"]:
                self.assertTrue(provider["endpoint"].startswith("https://"), key)

    def test_config_does_not_contain_api_key(self):
        config = pocketagent.default_config()
        serialized = json.dumps(config)
        self.assertNotIn("apiKey", serialized)
        self.assertNotIn("API_KEY", serialized)

    def test_tools_can_be_omitted_from_payload(self):
        self.config["tools"]["enabled"] = False
        self.assertFalse(self.config["tools"]["enabled"])

    def test_remote_http_endpoint_is_rejected(self):
        with self.assertRaises(ValueError):
            pocketagent.validate_endpoint("http://example.com/v1/chat/completions")

    def test_report_config_excludes_endpoint_path_and_secret(self):
        self.config["endpoint"] = "https://example.com/private/chat/completions"
        safe = pocketagent.safe_config_for_report(self.config)
        serialized = json.dumps(safe)
        self.assertEqual(safe["endpointHost"], "example.com")
        self.assertNotIn("/private/", serialized)
        self.assertNotIn("apiKey", serialized)

    def test_generated_report_redacts_log_secret(self):
        self.config["endpoint"] = "https://example.com/v1/chat/completions"
        self.config["model"] = "test-model"
        os.makedirs(pocketagent.LOGS_DIR)
        os.makedirs(self.workspace, exist_ok=True)
        pocketagent.atomic_write_json(pocketagent.CONFIG_PATH, self.config)
        pocketagent.save_api_key("sk-report-secret-123456")
        with open(os.path.join(pocketagent.LOGS_DIR, "gateway.log"), "w", encoding="utf-8") as handle:
            handle.write("request failed with sk-report-secret-123456\n")

        original_check_url = pocketagent.check_url
        original_capture = pocketagent.capture_command
        pocketagent.check_url = lambda _url, timeout=8, method="HEAD": (True, "mock")
        pocketagent.capture_command = lambda _args, timeout=10: "mock"
        report_path = os.path.join(self.temp, "report.txt")
        try:
            pocketagent.generate_report(False, report_path)
        finally:
            pocketagent.check_url = original_check_url
            pocketagent.capture_command = original_capture

        with open(report_path, "r", encoding="utf-8") as handle:
            report = handle.read()
        self.assertNotIn("sk-report-secret-123456", report)
        self.assertIn("***REDACTED***", report)

    def test_agent_tool_loop_end_to_end(self):
        class ThreadedServer(ThreadingMixIn, HTTPServer):
            daemon_threads = True

        class FakeModelHandler(BaseHTTPRequestHandler):
            requests = []

            def log_message(self, _format, *_args):
                return

            def do_POST(self):
                length = int(self.headers.get("Content-Length", "0"))
                payload = json.loads(self.rfile.read(length).decode("utf-8"))
                self.__class__.requests.append(payload)
                if len(self.__class__.requests) == 1:
                    message = {
                        "role": "assistant",
                        "content": None,
                        "tool_calls": [{
                            "id": "call-1",
                            "type": "function",
                            "function": {
                                "name": "list_files",
                                "arguments": {"path": "."}
                            }
                        }]
                    }
                else:
                    message = {"role": "assistant", "content": [{"text": "工具调用成功"}]}
                body = json.dumps({"choices": [{"message": message}]}).encode("utf-8")
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

        server = ThreadedServer(("127.0.0.1", 0), FakeModelHandler)
        thread = threading.Thread(target=server.serve_forever)
        thread.daemon = True
        thread.start()
        try:
            self.config["endpoint"] = "http://127.0.0.1:%d/chat/completions" % server.server_port
            self.config["model"] = "fake-agent"
            pocketagent.atomic_write_json(pocketagent.CONFIG_PATH, self.config)
            pocketagent.save_api_key("test-secret")
            reply, events = pocketagent.chat_once("列出文件", "integration")
            self.assertEqual(reply, "工具调用成功")
            self.assertEqual(events[0]["tool"], "list_files")
            self.assertIn("tools", FakeModelHandler.requests[0])
            self.assertEqual(FakeModelHandler.requests[1]["messages"][-1]["role"], "tool")
        finally:
            server.shutdown()
            server.server_close()


if __name__ == "__main__":
    unittest.main()
