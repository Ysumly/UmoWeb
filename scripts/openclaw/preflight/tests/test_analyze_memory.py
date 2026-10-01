import json
import importlib.util
import subprocess
import sys
import tempfile
import unittest
from datetime import datetime, timedelta
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "analyze_memory", SCRIPT_DIR / "analyze-memory.py"
)
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)

REQUIRED_SAMPLE_COUNT = MODULE.REQUIRED_SAMPLE_COUNT
THRESHOLD_KIB = MODULE.THRESHOLD_KIB
analyze_csv = MODULE.analyze_csv


HEADER = (
    "timestamp,mem_total_kib,mem_available_kib,swap_total_kib,swap_free_kib,"
    "umoweb_frontend_bytes,umoweb_backend_bytes,umoweb_mysql_bytes,"
    "umoweb_health,openclaw_service_state"
)


def sample(
    timestamp,
    mem_available_kib=600000,
    swap_free_kib=1000000,
    health="healthy",
    openclaw_state="not-installed",
):
    return (
        f"{timestamp},{1782760},{mem_available_kib},1049596,{swap_free_kib},"
        f"4000000,250000000,600000000,{health},{openclaw_state}"
    )


class AnalyzeMemoryTest(unittest.TestCase):
    def write_csv(self, rows):
        temp_dir = tempfile.TemporaryDirectory()
        path = Path(temp_dir.name) / "memory.csv"
        path.write_text("\n".join([HEADER, *rows]) + "\n", encoding="utf-8")
        self.addCleanup(temp_dir.cleanup)
        return path

    def full_rows(self, **kwargs):
        return [
            sample(
                (
                    datetime(2026, 10, 1)
                    + timedelta(days=day, hours=hour, minutes=minute * 5)
                ).strftime("%Y-%m-%dT%H:%M:%SZ"),
                **kwargs,
            )
            for day in range(3)
            for hour in range(24)
            for minute in range(12)
        ]

    def test_default_gate_threshold_is_480_mib(self):
        self.assertEqual(480 * 1024, THRESHOLD_KIB)

    def test_passes_with_required_count_and_all_samples_above_threshold(self):
        report = analyze_csv(self.write_csv(self.full_rows()))

        self.assertTrue(report["pass"])
        self.assertEqual(REQUIRED_SAMPLE_COUNT, report["sample_count"])
        self.assertEqual(0, report["below_threshold_count"])

    def test_fails_when_any_sample_is_at_or_below_threshold(self):
        rows = self.full_rows()
        rows[17] = sample(
            "2026-10-01T01:25:00Z",
            mem_available_kib=THRESHOLD_KIB,
        )

        report = analyze_csv(self.write_csv(rows))

        self.assertFalse(report["pass"])
        self.assertEqual(1, report["below_threshold_count"])
        self.assertEqual(THRESHOLD_KIB, report["min_mem_available_kib"])

    def test_swap_usage_does_not_change_mem_available_decision(self):
        rows = self.full_rows(swap_free_kib=900000)
        rows[-1] = sample(
            "2026-10-03T23:55:00Z",
            mem_available_kib=600000,
            swap_free_kib=100000,
        )

        report = analyze_csv(self.write_csv(rows))

        self.assertTrue(report["pass"])
        self.assertEqual(600000, report["min_mem_available_kib"])
        self.assertEqual(0, report["below_threshold_count"])

    def test_reports_p05_and_median(self):
        rows = self.full_rows(mem_available_kib=600000)
        rows[-1] = sample(
            "2026-10-03T23:55:00Z",
            mem_available_kib=400000,
        )

        report = analyze_csv(self.write_csv(rows))

        self.assertEqual(400000, report["min_mem_available_kib"])
        self.assertEqual(600000, report["p05_mem_available_kib"])
        self.assertEqual(600000, report["median_mem_available_kib"])

    def test_rejects_empty_file(self):
        temp_dir = tempfile.TemporaryDirectory()
        path = Path(temp_dir.name) / "empty.csv"
        path.write_text("", encoding="utf-8")
        self.addCleanup(temp_dir.cleanup)

        with self.assertRaisesRegex(ValueError, "empty"):
            analyze_csv(path)

    def test_rejects_missing_field(self):
        path = self.write_csv(
            [
                "2026-10-01T00:00:00Z,1782760,600000,1049596,1000000,"
                "4000000,250000000,600000000,healthy"
            ]
        )

        with self.assertRaisesRegex(ValueError, "field count"):
            analyze_csv(path)

    def test_rejects_invalid_integer(self):
        rows = self.full_rows()
        rows[0] = rows[0].replace(",600000,", ",not-a-number,")

        with self.assertRaisesRegex(ValueError, "mem_available_kib"):
            analyze_csv(self.write_csv(rows))

    def test_rejects_invalid_timestamp(self):
        rows = self.full_rows()
        rows[0] = sample("not-a-timestamp")

        with self.assertRaisesRegex(ValueError, "timestamp"):
            analyze_csv(self.write_csv(rows))

    def test_cli_prints_json_for_valid_incomplete_gate(self):
        path = self.write_csv(self.full_rows()[:2])

        result = subprocess.run(
            [sys.executable, str(SCRIPT_DIR / "analyze-memory.py"), str(path)],
            check=False,
            capture_output=True,
            text=True,
        )

        self.assertEqual(0, result.returncode, result.stderr)
        payload = json.loads(result.stdout)
        self.assertFalse(payload["pass"])
        self.assertEqual(2, payload["sample_count"])

    def test_cli_rejects_invalid_input(self):
        temp_dir = tempfile.TemporaryDirectory()
        path = Path(temp_dir.name) / "empty.csv"
        path.write_text("", encoding="utf-8")
        self.addCleanup(temp_dir.cleanup)

        result = subprocess.run(
            [sys.executable, str(SCRIPT_DIR / "analyze-memory.py"), str(path)],
            check=False,
            capture_output=True,
            text=True,
        )

        self.assertEqual(1, result.returncode)
        self.assertIn("error:", result.stderr)


if __name__ == "__main__":
    unittest.main()
