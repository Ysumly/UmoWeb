import sys
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import report_server


class ReportServerTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        report_server.REPORT_PATH = Path(self.temp_dir.name) / "index.html"
        report_server.REPORT_PATH.write_text("<!doctype html><title>report</title>\n", encoding="utf-8")
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), report_server.ReportHandler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.base_url = f"http://127.0.0.1:{self.server.server_port}"

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=2)
        self.temp_dir.cleanup()

    def test_serves_only_health_and_report_without_caching(self):
        with urllib.request.urlopen(f"{self.base_url}/healthz") as response:
            self.assertEqual(response.status, 200)
            self.assertEqual(response.read(), b"ok\n")
            self.assertEqual(response.headers["Cache-Control"], "no-store")

        with urllib.request.urlopen(f"{self.base_url}/index.html") as response:
            self.assertEqual(response.status, 200)
            self.assertIn(b"<title>report</title>", response.read())
            self.assertIn("default-src 'none'", response.headers["Content-Security-Policy"])

        with self.assertRaises(urllib.error.HTTPError) as raised:
            urllib.request.urlopen(f"{self.base_url}/other")
        self.assertEqual(raised.exception.code, 404)


if __name__ == "__main__":
    unittest.main()
