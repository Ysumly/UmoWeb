import json
import re
import unittest
from collections import Counter
from pathlib import Path


CASES_PATH = Path(__file__).with_name("cases.json")

ALLOWED_MODE_KEYS = {
    "STRUCTURE_CLEANUP",
    "MODERN_TO_CLASSICAL",
    "ENGLISH_TO_CHINESE",
    "CHINESE_TO_ENGLISH",
    "LIGHT_NOVELIZATION",
}

MAX_SOURCE_CHARS = 2000


def _sensitive_findings(source):
    findings = []
    checks = [
        (
            "IPv4-like address",
            re.compile(r"(?<![\d.])(?:\d{1,3}\.){3}\d{1,3}(?![\d.])"),
        ),
        (
            "real domain",
            re.compile(
                r"\b[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"
                r"\.(?:com|org|net|cn|io|dev|ai|co)\b",
                re.IGNORECASE,
            ),
        ),
        (
            "access key marker",
            re.compile(r"LTAI[0-9A-Za-z]{12,}|AKIA[0-9A-Z]{16}", re.IGNORECASE),
        ),
        (
            "JWT-like token",
            re.compile(
                r"eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}"
            ),
        ),
        (
            "private key marker",
            re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
        ),
        (
            "credential keyword",
            re.compile(
                r"\b(password|passwd|secret|api[_-]?key|credential)\b",
                re.IGNORECASE,
            ),
        ),
        (
            "server credential variable",
            re.compile(
                r"\b(DEEPSEEK_API_KEY|DB_PASS|DB_PASSWORD|JWT_SECRET|ADMIN_PASSWORD)\b",
                re.IGNORECASE,
            ),
        ),
    ]

    for label, pattern in checks:
        if pattern.search(source):
            findings.append(label)

    return findings


class AiModeQualityCasesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not CASES_PATH.exists():
            raise AssertionError(f"missing cases file: {CASES_PATH}")
        cls.cases = json.loads(CASES_PATH.read_text(encoding="utf-8"))

    def test_root_has_cases_list(self):
        self.assertIsInstance(self.cases, dict)
        self.assertIn("cases", self.cases)
        self.assertIsInstance(self.cases["cases"], list)

    def test_exactly_ten_cases(self):
        self.assertEqual(len(self.cases["cases"]), 10)

    def test_unique_ids(self):
        ids = [case["id"] for case in self.cases["cases"]]
        self.assertEqual(len(ids), len(set(ids)))

    def test_two_cases_for_each_mode(self):
        counts = Counter(case["modeKey"] for case in self.cases["cases"])
        self.assertEqual(set(counts), ALLOWED_MODE_KEYS)
        for mode_key in ALLOWED_MODE_KEYS:
            self.assertEqual(counts[mode_key], 2)

    def test_sources_are_nonblank(self):
        for case in self.cases["cases"]:
            self.assertIsInstance(case["source"], str)
            self.assertTrue(case["source"].strip(), case["id"])

    def test_sources_are_under_limit(self):
        for case in self.cases["cases"]:
            self.assertLess(
                len(case["source"]),
                MAX_SOURCE_CHARS,
                case["id"],
            )

    def test_criteria_are_string_lists(self):
        for case in self.cases["cases"]:
            criteria = case["criteria"]
            self.assertIsInstance(criteria, list, case["id"])
            self.assertGreater(len(criteria), 0, case["id"])
            for criterion in criteria:
                self.assertIsInstance(criterion, str, case["id"])
                self.assertTrue(criterion.strip(), case["id"])

    def test_sources_have_no_sensitive_data(self):
        for case in self.cases["cases"]:
            findings = _sensitive_findings(case["source"])
            self.assertEqual(findings, [], f"{case['id']}: {findings}")


if __name__ == "__main__":
    unittest.main()
