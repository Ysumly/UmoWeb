import gzip
import json
import os
import sys
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from access_maintenance import (
    aggregate_records,
    load_config,
    parse_access_log_line,
    render_privacy_config,
    render_report,
    render_trusted_proxies,
    run_maintenance,
)


def access_record(
    time="2026-09-13T10:00:00+08:00",
    ip="203.0.113.10",
    method="GET",
    path="/library",
    status=200,
    bytes_sent=512,
):
    return {
        "time": time,
        "ip": ip,
        "method": method,
        "path": path,
        "status": status,
        "bytes": bytes_sent,
    }


class AccessLogParsingTests(unittest.TestCase):
    def test_parses_exact_six_field_payload(self):
        record = parse_access_log_line(json.dumps(access_record(), ensure_ascii=False))

        self.assertEqual(record["ip"], "203.0.113.10")
        self.assertEqual(record["path"], "/library")
        self.assertEqual(record["status"], 200)

    def test_rejects_extra_fields_and_sensitive_payloads(self):
        payload = access_record()
        payload["query"] = "token=secret"
        with self.assertRaises(ValueError):
            parse_access_log_line(json.dumps(payload))

        payload = access_record()
        payload["path"] = "/search?token=secret"
        with self.assertRaises(ValueError):
            parse_access_log_line(json.dumps(payload))

    def test_rejects_invalid_status_and_ip(self):
        with self.assertRaises(ValueError):
            parse_access_log_line(json.dumps(access_record(ip="not-an-ip")))
        with self.assertRaises(ValueError):
            parse_access_log_line(json.dumps(access_record(status=99)))


class AccessAggregationTests(unittest.TestCase):
    def test_aggregates_by_local_day_with_exact_unique_ips(self):
        records = [
            access_record(ip="203.0.113.10", path="/a"),
            access_record(ip="203.0.113.10", path="/a"),
            access_record(ip="2001:db8::1", path="/b", status=404),
            access_record(time="2026-09-14T00:30:00+08:00", ip="203.0.113.11", path="/c"),
        ]

        aggregates = aggregate_records(records)

        self.assertEqual(aggregates["2026-09-13"]["requestCount"], 3)
        self.assertEqual(aggregates["2026-09-13"]["uniqueIpCount"], 2)
        self.assertEqual(aggregates["2026-09-13"]["statuses"], {"200": 2, "404": 1})
        self.assertEqual(aggregates["2026-09-13"]["topPaths"][0], {"path": "/a", "count": 2})
        self.assertEqual(aggregates["2026-09-14"]["uniqueIpCount"], 1)

        serialized = json.dumps(aggregates, ensure_ascii=False)
        self.assertNotIn("203.0.113.10", serialized)
        self.assertNotIn("2001:db8::1", serialized)

    def test_report_escapes_paths_and_does_not_render_query_values(self):
        records = [
            access_record(
                ip="203.0.113.10",
                path="/notes/<script>alert(1)</script>",
                status=200,
            )
        ]
        html = render_report(
            records,
            aggregate_records(records),
            generated_at=datetime(2026, 9, 13, 3, 0, tzinfo=timezone.utc),
        )

        self.assertIn("&lt;script&gt;", html)
        self.assertNotIn("<script>alert(1)</script>", html)
        self.assertIn("203.0.113.10", html)
        self.assertNotIn("token=", html)


class AccessConfigTests(unittest.TestCase):
    def test_render_config_validates_retention_and_generates_proxy_rules(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            config_path = Path(temp_dir) / "access.env"
            config_path.write_text(
                "\n".join(
                    [
                        f"ACCESS_LOG_DIR={Path(temp_dir) / 'logs'}",
                        f"ACCESS_AGGREGATE_DIR={Path(temp_dir) / 'aggregates'}",
                        f"ACCESS_REPORT_DIR={Path(temp_dir) / 'reports'}",
                        "ACCESS_RAW_RETENTION_DAYS=30",
                        "ACCESS_AGGREGATE_RETENTION_DAYS=180",
                        "ACCESS_TRUSTED_PROXIES=127.0.0.1, 2001:db8::/32",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )
            config = load_config(config_path)

            self.assertIn("set_real_ip_from 127.0.0.1;", render_trusted_proxies(config))
            self.assertIn("set_real_ip_from 2001:db8::/32;", render_trusted_proxies(config))
            self.assertEqual(
                json.loads(render_privacy_config(config)),
                {"rawRetentionDays": 30, "aggregateRetentionDays": 180},
            )

    def test_rejects_raw_retention_outside_supported_range(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            config_path = Path(temp_dir) / "access.env"
            config_path.write_text(
                f"ACCESS_LOG_DIR={Path(temp_dir) / 'logs'}\n"
                f"ACCESS_AGGREGATE_DIR={Path(temp_dir) / 'aggregates'}\n"
                f"ACCESS_REPORT_DIR={Path(temp_dir) / 'reports'}\n"
                "ACCESS_RAW_RETENTION_DAYS=31\n",
                encoding="utf-8",
            )
            with self.assertRaises(ValueError):
                load_config(config_path)


class AccessRetentionTests(unittest.TestCase):
    def test_maintenance_prunes_raw_and_aggregate_boundaries(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            log_dir = root / "logs"
            aggregate_dir = root / "aggregates"
            report_dir = root / "reports"
            log_dir.mkdir()
            aggregate_dir.mkdir()

            config_path = root / "access.env"
            config_path.write_text(
                "\n".join(
                    [
                        f"ACCESS_LOG_DIR={log_dir}",
                        f"ACCESS_AGGREGATE_DIR={aggregate_dir}",
                        f"ACCESS_REPORT_DIR={report_dir}",
                        "ACCESS_RAW_RETENTION_DAYS=7",
                        "ACCESS_AGGREGATE_RETENTION_DAYS=180",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )
            config = load_config(config_path)

            old_log = log_dir / "access-20260906T000000Z.log.gz"
            boundary_log = log_dir / "access-20260907T000000Z.log.gz"
            current_log = log_dir / "access-20260913T000000Z.log.gz"
            for path, record in (
                (old_log, access_record(time="2026-09-06T08:00:00+08:00")),
                (boundary_log, access_record(time="2026-09-07T08:00:00+08:00")),
                (current_log, access_record(time="2026-09-13T08:00:00+08:00")),
            ):
                with gzip.open(path, "wt", encoding="utf-8") as handle:
                    handle.write(json.dumps(record) + "\n")

            (aggregate_dir / "2026-03-16.json").write_text("{}\n", encoding="utf-8")
            (aggregate_dir / "2026-03-17.json").write_text("{}\n", encoding="utf-8")

            result = run_maintenance(
                config,
                now=datetime(2026, 9, 13, 2, 0, tzinfo=timezone.utc),
            )

            self.assertFalse(old_log.exists())
            self.assertTrue(boundary_log.exists())
            self.assertTrue(current_log.exists())
            self.assertFalse((aggregate_dir / "2026-03-16.json").exists())
            self.assertTrue((aggregate_dir / "2026-03-17.json").exists())
            self.assertTrue((report_dir / "index.html").exists())
            if os.name != "nt":
                self.assertEqual(os.stat(report_dir / "index.html").st_mode & 0o777, 0o640)
            self.assertEqual(result["records"], 2)
            self.assertEqual(result["invalid"], 0)


if __name__ == "__main__":
    unittest.main()
