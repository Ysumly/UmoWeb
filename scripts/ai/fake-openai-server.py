#!/usr/bin/env python3
"""Minimal local OpenAI-compatible provider used only by automated tests.

The service intentionally listens only on loopback and never reads DeepSeek
credentials or any other environment secret.
"""

from __future__ import annotations

import argparse
import json
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any


class FakeOpenAiHandler(BaseHTTPRequestHandler):
    server_version = "FakeOpenAi/1.0"

    def do_POST(self) -> None:
        if self.path != "/chat/completions":
            self._send_json(
                404,
                {
                    "error": {
                        "message": "Not Found",
                        "type": "not_found_error",
                        "code": "not_found",
                    }
                },
            )
            return

        transfer_encoding = self.headers.get("Transfer-Encoding", "")
        if transfer_encoding.strip().lower() == "chunked":
            try:
                raw_body = self._read_chunked_body()
            except ValueError:
                self._send_invalid_json()
                return
        else:
            content_length = self.headers.get("Content-Length", "0")
            try:
                length = int(content_length)
            except ValueError:
                self._send_invalid_json()
                return
            raw_body = self.rfile.read(length)
        try:
            request: dict[str, Any] = json.loads(raw_body.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            self._send_invalid_json()
            return

        if not isinstance(request, dict):
            self._send_invalid_json()
            return

        messages = request.get("messages")
        if not isinstance(messages, list):
            self._send_invalid_json()
            return

        user_contents = [
            message.get("content", "")
            for message in messages
            if isinstance(message, dict) and message.get("role") == "user"
        ]
        if not user_contents:
            self._send_invalid_json()
            return

        system_contents = [
            message.get("content", "")
            for message in messages
            if isinstance(message, dict) and message.get("role") == "system"
        ]

        content = str(user_contents[-1])
        input_characters = sum(len(str(value)) for value in system_contents + user_contents)
        output_characters = len(content)

        self._send_json(
            200,
            {
                "id": "chatcmpl-fake-local",
                "object": "chat.completion",
                "created": int(time.time()),
                "model": request.get("model", "fake-model"),
                "choices": [
                    {
                        "index": 0,
                        "message": {"role": "assistant", "content": content},
                        "finish_reason": "stop",
                    }
                ],
                "usage": {
                    "input_tokens": input_characters,
                    "output_tokens": output_characters,
                    "prompt_tokens": input_characters,
                    "completion_tokens": output_characters,
                    "total_tokens": input_characters + output_characters,
                },
            },
        )

    def do_GET(self) -> None:
        self._send_json(
            404,
            {
                "error": {
                    "message": "Not Found",
                    "type": "not_found_error",
                    "code": "not_found",
                }
            },
        )

    def _send_invalid_json(self) -> None:
        self._send_json(
            400,
            {
                "error": {
                    "message": "Invalid JSON body",
                    "type": "invalid_request_error",
                    "param": None,
                    "code": "invalid_json",
                }
            },
        )

    def _read_chunked_body(self) -> bytes:
        chunks: list[bytes] = []
        while True:
            size_line = self.rfile.readline()
            if not size_line:
                raise ValueError("unexpected end of chunk size")
            size_token = size_line.split(b";", 1)[0].strip()
            try:
                chunk_size = int(size_token, 16)
            except ValueError as exc:
                raise ValueError("invalid chunk size") from exc
            if chunk_size < 0:
                raise ValueError("negative chunk size")
            if chunk_size == 0:
                while True:
                    trailer = self.rfile.readline()
                    if not trailer:
                        raise ValueError("unexpected end of chunk trailer")
                    if trailer in (b"\r\n", b"\n"):
                        return b"".join(chunks)

            chunk = self.rfile.read(chunk_size)
            if len(chunk) != chunk_size:
                raise ValueError("unexpected end of chunk data")
            if self.rfile.read(2) != b"\r\n":
                raise ValueError("invalid chunk terminator")
            chunks.append(chunk)

    def _send_json(self, status: int, payload: dict[str, Any]) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format_string: str, *args: Any) -> None:
        return


class FakeOpenAiServer(ThreadingHTTPServer):
    allow_reuse_address = True
    daemon_threads = True


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=19090, help="loopback port to bind")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    server = FakeOpenAiServer(("127.0.0.1", args.port), FakeOpenAiHandler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
