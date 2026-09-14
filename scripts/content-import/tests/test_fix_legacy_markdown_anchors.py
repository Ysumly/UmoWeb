import hashlib
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPT_ROOT))

import fix_legacy_markdown_anchors as subject


def file_sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


class FixLegacyMarkdownAnchorsTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name) / "contents"
        self.root.mkdir()
        self.backup_dir = Path(self.temp_dir.name) / "backup"

    def tearDown(self):
        self.temp_dir.cleanup()

    def write_fixture(self, name: str, body: str) -> Path:
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(body, encoding="utf-8", newline="")
        return path

    def test_dry_run_reports_known_replacements_without_writing(self):
        path = self.write_fixture(
            "note.md",
            "\n".join(
                [
                    "- [缓存的工作方式](#缓存的工作方式)",
                    "- [混合方案](#混合方案-array-of-structures-of-arrays-aosoa)",
                    "##### <span id=\"通过C#脚本创建一个场景\">通过C#脚本创建场景</span>",
                    "- [场景](#通过C#脚本创建一个场景)",
                    "",
                ]
            ),
        )
        before = file_sha256(path)

        report = subject.fix_legacy_markdown_anchors(self.root, apply=False)

        self.assertFalse(report["applied"])
        self.assertEqual(report["changed_files"], 1)
        self.assertEqual(report["replacements"], 4)
        self.assertEqual(file_sha256(path), before)
        self.assertFalse(self.backup_dir.exists())

    def test_apply_backs_up_and_atomically_updates_idempotently(self):
        path = self.write_fixture(
            "note.md",
            "\n".join(
                [
                    "- [缓存的工作方式](#缓存的工作方式)",
                    "- [混合方案](#混合方案-array-of-structures-of-arrays-aosoa)",
                    "##### <span id=\"通过C#脚本创建一个场景\">通过C#脚本创建场景</span>",
                    "- [场景](#通过C#脚本创建一个场景)",
                    "",
                ]
            ),
        )
        before = file_sha256(path)

        report = subject.fix_legacy_markdown_anchors(
            self.root,
            apply=True,
            backup_dir=self.backup_dir,
        )

        self.assertTrue(report["applied"])
        self.assertEqual(report["changed_files"], 1)
        self.assertEqual(report["replacements"], 4)
        self.assertEqual(file_sha256(self.backup_dir / "note.md"), before)
        self.assertEqual(file_sha256(path), report["files"][0]["after_sha256"])
        content = path.read_text(encoding="utf-8")
        self.assertIn("#缓存的工作原理", content)
        self.assertIn("#混合方案array-of-structures-of-arrays-aosoa", content)
        self.assertIn("##### 通过C#脚本创建一个场景", content)
        self.assertIn("#通过c脚本创建一个场景", content)
        self.assertNotIn("<span", content)

        second = subject.fix_legacy_markdown_anchors(
            self.root,
            apply=True,
            backup_dir=self.backup_dir,
        )
        self.assertEqual(second["changed_files"], 0)
        self.assertEqual(second["replacements"], 0)
        self.assertEqual(file_sha256(path), report["files"][0]["after_sha256"])

    def test_apply_requires_a_backup_directory(self):
        self.write_fixture(
            "note.md",
            "- [缓存的工作方式](#缓存的工作方式)\n",
        )

        with self.assertRaisesRegex(subject.LegacyAnchorError, "backup"):
            subject.fix_legacy_markdown_anchors(self.root, apply=True)

    def test_rejects_missing_targets_without_writing(self):
        path = self.write_fixture("note.md", "# 不相关内容\n")
        before = file_sha256(path)

        with self.assertRaisesRegex(
            subject.LegacyAnchorError,
            "targets",
        ):
            subject.fix_legacy_markdown_anchors(self.root, apply=False)
        self.assertEqual(file_sha256(path), before)
