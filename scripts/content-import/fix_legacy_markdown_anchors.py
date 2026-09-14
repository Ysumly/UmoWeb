"""Normalize known legacy Markdown anchors without touching unrelated content."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import sys
import tempfile
from pathlib import Path
from typing import Sequence


class LegacyAnchorError(RuntimeError):
    """Raised when a legacy anchor migration cannot be completed safely."""


REPLACEMENTS: tuple[tuple[str, str], ...] = (
    ("#缓存的工作方式", "#缓存的工作原理"),
    (
        "#混合方案-array-of-structures-of-arrays-aosoa",
        "#混合方案array-of-structures-of-arrays-aosoa",
    ),
    (
        '##### <span id="通过C#脚本创建一个场景">通过C#脚本创建场景</span>',
        "##### 通过C#脚本创建一个场景",
    ),
    ("#通过C#脚本创建一个场景", "#通过c脚本创建一个场景"),
)


def _sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def _resolve_backup_path(backup_dir: Path, relative_path: Path) -> Path:
    if relative_path.is_absolute() or ".." in relative_path.parts:
        raise LegacyAnchorError(f"unsafe Markdown path: {relative_path}")
    return backup_dir / relative_path


def _atomic_write(path: Path, content: str) -> None:
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=f".{path.name}.",
        suffix=".tmp",
        dir=path.parent,
    )
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="") as handle:
            handle.write(content)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary_name, path)
    except Exception:
        try:
            os.unlink(temporary_name)
        except FileNotFoundError:
            pass
        raise


def fix_legacy_markdown_anchors(
    root: Path,
    *,
    apply: bool = False,
    backup_dir: Path | None = None,
) -> dict[str, object]:
    root = Path(root).resolve()
    if not root.is_dir():
        raise LegacyAnchorError(f"Markdown root is not a directory: {root}")
    if apply and backup_dir is None:
        raise LegacyAnchorError("--backup-dir is required with --apply")

    backup_root = Path(backup_dir).resolve() if backup_dir else None
    file_reports: list[dict[str, object]] = []
    total_replacements = 0
    already_normalized = False

    for path in sorted(root.rglob("*.md")):
        before_bytes = path.read_bytes()
        try:
            before = before_bytes.decode("utf-8")
        except UnicodeDecodeError as exc:
            raise LegacyAnchorError(f"Markdown is not UTF-8: {path}") from exc

        after = before
        replacements = 0
        for old, new in REPLACEMENTS:
            occurrences = after.count(old)
            if occurrences:
                replacements += occurrences
                after = after.replace(old, new)
            if new in after:
                already_normalized = True

        if replacements == 0:
            continue

        relative_path = path.relative_to(root)
        before_sha256 = _sha256_bytes(before_bytes)
        after_bytes = after.encode("utf-8")
        after_sha256 = _sha256_bytes(after_bytes)
        backup_path = None

        if apply:
            assert backup_root is not None
            destination = _resolve_backup_path(backup_root, relative_path)
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, destination)
            _atomic_write(path, after)
            backup_path = str(destination)

        file_reports.append(
            {
                "path": str(relative_path),
                "replacements": replacements,
                "before_sha256": before_sha256,
                "after_sha256": after_sha256,
                "backup": backup_path,
            }
        )
        total_replacements += replacements

    if not file_reports and not already_normalized:
        raise LegacyAnchorError("legacy anchor targets were not found")

    return {
        "applied": apply,
        "root": str(root),
        "changed_files": len(file_reports),
        "replacements": total_replacements,
        "files": file_reports,
    }


def _parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Fix known legacy Markdown anchors.",
    )
    parser.add_argument("--root", required=True, type=Path)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--backup-dir", type=Path)
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    args = _parse_args(argv)
    try:
        report = fix_legacy_markdown_anchors(
            args.root,
            apply=args.apply,
            backup_dir=args.backup_dir,
        )
    except LegacyAnchorError as exc:
        print(f"legacy anchor migration failed: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
