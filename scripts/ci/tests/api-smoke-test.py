import contextlib
import importlib.util
import io
import json
import pathlib
import unittest
from unittest import mock

import urllib.error


REPO_ROOT = pathlib.Path(__file__).resolve().parents[3]
SMOKE_MODULE_PATH = (
    REPO_ROOT
    / "Server Side"
    / "UmoWebBackend"
    / "scripts"
    / "api-smoke.py"
)

spec = importlib.util.spec_from_file_location("umo_api_smoke", SMOKE_MODULE_PATH)
api_smoke = importlib.util.module_from_spec(spec)
spec.loader.exec_module(api_smoke)


ORIGINAL_PROMPT = "你是测试转换器。"
UPDATED_PROMPT = "你是测试转换器第二版。"
DEFAULT_MODE_KEYS = {
    "STRUCTURE_CLEANUP",
    "MODERN_TO_CLASSICAL",
    "ENGLISH_TO_CHINESE",
    "CHINESE_TO_ENGLISH",
    "LIGHT_NOVELIZATION",
}


class FakeResponse:
    status = 200

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, traceback):
        return False

    def read(self):
        return b"{}"


class ScriptedSmoke(api_smoke.Smoke):
    def __init__(self):
        super().__init__("http://mock", "admin", "password")
        self.run_id = "0123456789abcdef0123456789abcdef"
        self.token = "fake-token"
        self.total_endpoints = 40
        self.steps = 31
        self.request_count = 0
        self.ai_mode_id = None
        self.ai_copy_mode_id = None
        self.test_mode_key = None
        self.copy_mode_key = None
        self.current_version = 1
        self.current_prompt = ORIGINAL_PROMPT
        self.mode_enabled = False
        self.update_v1_attempts = 0

    def request(
        self,
        method,
        path,
        body=None,
        *,
        token=None,
        expected=200,
    ):
        self.request_count += 1

        if path == "/api/admin/ai/modes" and method == "GET":
            modes = [
                self.settings_payload(identifier, mode_key, False, 1, ORIGINAL_PROMPT)
                for identifier, mode_key in enumerate(DEFAULT_MODE_KEYS, 1)
            ]
            return 200, json.dumps(modes, ensure_ascii=False)

        if path == "/api/admin/ai/modes" and method == "POST":
            self.test_mode_key = body["modeKey"]
            self.ai_mode_id = 501
            self.current_version = 1
            self.current_prompt = body["systemPrompt"]
            self.mode_enabled = False
            payload = self.settings_payload(
                501,
                self.test_mode_key,
                False,
                1,
                self.current_prompt,
            )
            payload["name"] = body["name"]
            payload["description"] = body.get("description", "")
            payload["sortOrder"] = body.get("sortOrder", 999)
            return 200, json.dumps(payload, ensure_ascii=False)

        if method == "PUT" and path.startswith("/api/admin/ai/modes/"):
            expected_version = body.get("expectedVersion")
            if expected_version == 1:
                self.update_v1_attempts += 1
                if self.update_v1_attempts == 1:
                    self.current_version = 2
                    self.current_prompt = body["systemPrompt"]
                    self.mode_enabled = body.get("enabled", False)
                    payload = self.settings_payload(
                        501,
                        self.test_mode_key,
                        self.mode_enabled,
                        2,
                        self.current_prompt,
                    )
                    return 200, json.dumps(payload, ensure_ascii=False)
                return 409, json.dumps(
                    {"code": 409, "message": "模式已在其他窗口更新，请重新加载"},
                    ensure_ascii=False,
                )
            if expected_version == 3:
                self.current_version = 3
                self.current_prompt = body["systemPrompt"]
                self.mode_enabled = body.get("enabled", False)
                payload = self.settings_payload(
                    501,
                    self.test_mode_key,
                    self.mode_enabled,
                    3,
                    self.current_prompt,
                )
                return 200, json.dumps(payload, ensure_ascii=False)
            return 409, json.dumps(
                {"code": 409, "message": "模式已在其他窗口更新，请重新加载"},
                ensure_ascii=False,
            )

        if method == "POST" and "/copy" in path:
            self.copy_mode_key = body["modeKey"]
            self.ai_copy_mode_id = 502
            payload = self.settings_payload(
                502,
                self.copy_mode_key,
                False,
                1,
                self.current_prompt,
            )
            payload["name"] = body["name"]
            payload["description"] = ""
            payload["sortOrder"] = 0
            return 200, json.dumps(payload, ensure_ascii=False)

        if method == "GET" and path.endswith("/versions"):
            versions = [
                {
                    "versionNo": 1,
                    "systemPrompt": ORIGINAL_PROMPT,
                    "validationProfile": "NONE",
                    "createdAt": "2026-09-18T10:00:00",
                },
                {
                    "versionNo": 2,
                    "systemPrompt": UPDATED_PROMPT,
                    "validationProfile": "NONE",
                    "createdAt": "2026-09-18T10:01:00",
                },
            ]
            return 200, json.dumps(versions, ensure_ascii=False)

        if method == "POST" and "/rollback/" in path:
            self.current_version = 3
            self.current_prompt = ORIGINAL_PROMPT
            payload = self.settings_payload(
                501,
                self.test_mode_key,
                self.mode_enabled,
                3,
                self.current_prompt,
            )
            return 200, json.dumps(payload, ensure_ascii=False)

        if path == "/api/admin/ai/settings" and method == "GET":
            payload = {
                "enabled": True,
                "provider": "deepseek",
                "model": "fake-model",
                "maxInputChars": 20_000,
                "maxOutputChars": 20_000,
                "modeCount": 6,
            }
            return 200, json.dumps(payload, ensure_ascii=False)

        if path == "/api/admin/ai/capabilities" and method == "GET":
            payload = {
                "enabled": True,
                "maxInputChars": 20_000,
                "modes": [
                    {
                        "modeKey": self.test_mode_key,
                        "name": "Smoke AI Mode",
                        "description": "",
                    }
                ],
            }
            return 200, json.dumps(payload, ensure_ascii=False)

        if path == "/api/admin/ai/transform" and method == "POST":
            if not self.mode_enabled:
                return 409, json.dumps(
                    {
                        "code": 409,
                        "message": "AI 模式已停用（请求 ID: req-disabled-123）",
                    },
                    ensure_ascii=False,
                )
            content = body["content"]
            input_tokens = len(ORIGINAL_PROMPT) + len(content)
            output_tokens = len(content)
            payload = {
                "requestId": "req-transform-123",
                "modeKey": self.test_mode_key,
                "modeVersion": self.current_version,
                "content": content,
                "model": "fake-model",
                "usage": {
                    "inputTokens": input_tokens,
                    "outputTokens": output_tokens,
                    "totalTokens": input_tokens + output_tokens,
                },
            }
            return 200, json.dumps(payload, ensure_ascii=False)

        raise AssertionError(f"unexpected smoke request: {method} {path}")

    def settings_payload(self, identifier, mode_key, enabled, version, prompt):
        return {
            "id": identifier,
            "modeKey": mode_key,
            "name": "Smoke AI Mode",
            "description": "",
            "enabled": enabled,
            "sortOrder": 999,
            "currentVersion": version,
            "systemPrompt": prompt,
            "validationProfile": "NONE",
            "createdAt": "2026-09-18T10:00:00",
            "updatedAt": "2026-09-18T10:00:00",
        }


class ApiSmokeSelfTest(unittest.TestCase):
    def test_run_ai_flow_covers_nine_endpoints_and_asserts_contract(self):
        smoke = ScriptedSmoke()

        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            smoke.run_ai_flow()

        self.assertEqual(smoke.steps, 40)
        self.assertEqual(smoke.request_count, 12)
        self.assertIn("[40/40]", output.getvalue())
        self.assertEqual(smoke.ai_mode_id, 501)
        self.assertEqual(smoke.ai_copy_mode_id, 502)
        self.assertFalse(smoke.mode_enabled)

    def test_request_count_is_separate_from_endpoint_count(self):
        smoke = api_smoke.Smoke("http://mock", "admin", "password")
        with mock.patch.object(
            api_smoke.urllib.request,
            "urlopen",
            return_value=FakeResponse(),
        ):
            smoke.request("GET", "/one")
            smoke.request("GET", "/two")

        smoke.step(1, "GET /one")
        smoke.step(2, "GET /two")

        self.assertEqual(smoke.request_count, 2)
        self.assertEqual(smoke.steps, 2)

    def test_failure_output_redacts_body_and_keeps_request_id(self):
        smoke = api_smoke.Smoke("http://mock", "admin", "password")
        secret = "S3cr3tBody-Should-Not-Leak"
        request_id = "req-123"
        response_body = json.dumps(
            {
                "code": 409,
                "message": f"AI 模式已停用（请求 ID: {request_id}）",
                "content": secret,
            },
            ensure_ascii=False,
        ).encode("utf-8")
        error = urllib.error.HTTPError(
            "http://mock/api/admin/ai/transform",
            409,
            "Conflict",
            {},
            io.BytesIO(response_body),
        )

        with mock.patch.object(
            api_smoke.urllib.request,
            "urlopen",
            side_effect=error,
        ):
            with self.assertRaises(api_smoke.SmokeFailure) as raised:
                smoke.request(
                    "POST",
                    "/api/admin/ai/transform",
                    {"modeKey": "CI_AI_MODE_TEST", "content": secret},
                    token="token-secret",
                )

        message = str(raised.exception)
        self.assertNotIn(secret, message)
        self.assertNotIn("token-secret", message)
        self.assertIn(request_id, message)


if __name__ == "__main__":
    unittest.main()
