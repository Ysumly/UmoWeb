#!/usr/bin/env python3
"""Serve the generated UmoWeb access report on loopback only."""

from __future__ import annotations

import argparse
import ipaddress
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


REPORT_PATH = Path("/opt/umoweb/access/reports/index.html")


class ReportHandler(BaseHTTPRequestHandler):
    server_version = "UmoWebAccessReport"
    sys_version = ""

    def _send_plain(self, status: int, body: bytes, content_type: str) -> None:
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Referrer-Policy", "no-referrer")
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def do_HEAD(self) -> None:
        self.do_GET()

    def do_GET(self) -> None:
        if self.path == "/healthz":
            self._send_plain(200, b"ok\n", "text/plain; charset=utf-8")
            return
        if self.path not in {"/", "/index.html"}:
            self._send_plain(404, b"not found\n", "text/plain; charset=utf-8")
            return
        try:
            body = REPORT_PATH.read_bytes()
        except FileNotFoundError:
            self._send_plain(503, b"report unavailable\n", "text/plain; charset=utf-8")
            return

        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header(
            "Content-Security-Policy",
            "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'",
        )
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def log_message(self, format_string: str, *args: object) -> None:
        return


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=7890)
    parser.add_argument("--report", default=str(REPORT_PATH))
    return parser.parse_args()


def main() -> int:
    arguments = parse_arguments()
    host = ipaddress.ip_address(arguments.host)
    if not host.is_loopback:
        raise SystemExit("access report server must bind to a loopback address")
    if not 1 <= arguments.port <= 65535:
        raise SystemExit("access report port must be between 1 and 65535")

    global REPORT_PATH
    REPORT_PATH = Path(arguments.report)
    if not REPORT_PATH.is_absolute():
        raise SystemExit("access report path must be absolute")
    if not REPORT_PATH.name == "index.html":
        raise SystemExit("access report path must point to index.html")

    server = ThreadingHTTPServer((arguments.host, arguments.port), ReportHandler)
    server.daemon_threads = True
    server.serve_forever()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
