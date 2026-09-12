#!/usr/bin/env python3
"""Build a restorable UmoWeb backup from a curated Markdown catalog."""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import mimetypes
import os
import re
import shutil
import sys
import tarfile
import tempfile
from dataclasses import dataclass
from datetime import date, datetime, timezone
from pathlib import Path
from typing import Any
from urllib.parse import unquote
from zoneinfo import ZoneInfo


DATABASE_NAME = "umo_blog"
BACKUP_HELPER_IMAGE = "mysql:8.4"
SAFE_SLUG = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_-]{0,199}$")
IMAGE_PATTERN = re.compile(r"!\[([^\]]*)\]\((.*?)\)")
LINK_PATTERN = re.compile(r"(?<!!)\[([^\]]+)\]\(([^)\s]+)(?:\s+[\"'][^\"']*[\"'])?\)")
HEADING_PATTERN = re.compile(r"^#{1,6}\s+")
SUPPORTED_IMAGE_TYPES = {
    ".gif": ("gif", "image/gif"),
    ".jpeg": ("jpg", "image/jpeg"),
    ".jpg": ("jpg", "image/jpeg"),
    ".png": ("png", "image/png"),
    ".webp": ("webp", "image/webp"),
}


class ContentImportError(RuntimeError):
    """Raised when source content cannot be converted safely."""


@dataclass(frozen=True)
class Catalog:
    categories: list[dict[str, Any]]
    tags: list[dict[str, Any]]
    articles: list[dict[str, Any]]
    site_options: dict[str, str]


@dataclass(frozen=True)
class GeneratedImage:
    original_name: str
    stored_name: str
    relative_path: str
    size: int
    content_type: str
    created_at: str


def derive_title(markdown: str, fallback: str) -> str:
    for line in markdown.splitlines():
        if line.startswith("# "):
            return line[2:].strip() or fallback
    return fallback


def derive_summary(markdown: str, title: str, max_chars: int = 180) -> str:
    in_fence = False
    paragraph: list[str] = []
    for line in markdown.splitlines():
        stripped = line.strip()
        if stripped.startswith("```") or stripped.startswith("~~~"):
            in_fence = not in_fence
            continue
        if in_fence:
            continue
        if not stripped:
            summary = _plain_text(" ".join(paragraph))
            if summary:
                return _truncate(summary, max_chars)
            paragraph = []
            continue
        if (
            HEADING_PATTERN.match(stripped)
            or stripped.startswith(">")
            or stripped.startswith("|")
            or stripped.startswith("![")
            or re.fullmatch(r"([-*_])(?:\s*\1){2,}", stripped)
            or stripped.startswith("- ")
            or stripped.startswith("* ")
            or re.match(r"^\d+[.)]\s+", stripped)
        ):
            continue
        paragraph.append(stripped)

    summary = _plain_text(" ".join(paragraph))
    if summary:
        return _truncate(summary, max_chars)
    return f"{title} 学习笔记。"


def load_catalog(path: Path) -> Catalog:
    try:
        payload = json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ContentImportError(f"failed to load catalog: {exc}") from exc

    categories = payload.get("categories")
    tags = payload.get("tags")
    articles = payload.get("articles")
    site_options = payload.get("site_options", {})
    if not isinstance(categories, list) or not categories:
        raise ContentImportError("catalog must contain a non-empty categories array")
    if not isinstance(tags, list):
        raise ContentImportError("catalog tags must be an array")
    if not isinstance(articles, list) or not articles:
        raise ContentImportError("catalog must contain a non-empty articles array")
    if not isinstance(site_options, dict):
        raise ContentImportError("catalog site_options must be an object")

    category_keys: set[str] = set()
    category_slugs: set[str] = set()
    for category in categories:
        _require_string(category, "key")
        _require_string(category, "name")
        _require_safe_slug(category.get("slug"), "category")
        key = category["key"]
        slug = category["slug"]
        if key in category_keys or slug in category_slugs:
            raise ContentImportError(f"duplicate category key or slug: {key}/{slug}")
        category_keys.add(key)
        category_slugs.add(slug)
        if category.get("parent") is not None and category["parent"] not in category_keys:
            raise ContentImportError(
                f"category parent must be declared first: {category['parent']}"
            )

    tag_keys: set[str] = set()
    tag_slugs: set[str] = set()
    for tag in tags:
        _require_string(tag, "key")
        _require_string(tag, "name")
        _require_safe_slug(tag.get("slug"), "tag")
        if tag["key"] in tag_keys or tag["slug"] in tag_slugs:
            raise ContentImportError(f"duplicate tag key or slug: {tag['key']}/{tag['slug']}")
        tag_keys.add(tag["key"])
        tag_slugs.add(tag["slug"])

    article_sources: set[str] = set()
    article_slugs: set[str] = set()
    for article in articles:
        _require_string(article, "source")
        _require_safe_slug(article.get("slug"), "article")
        category = article.get("category")
        if category not in category_keys:
            raise ContentImportError(f"article references unknown category: {category}")
        source = _normalize_relative(article["source"])
        if source in article_sources or article["slug"] in article_slugs:
            raise ContentImportError(
                f"duplicate article source or slug: {source}/{article['slug']}"
            )
        article_sources.add(source)
        article_slugs.add(article["slug"])
        for tag in article.get("tags", []):
            if tag not in tag_keys:
                raise ContentImportError(f"article references unknown tag: {tag}")

    for key, value in site_options.items():
        if not isinstance(key, str) or not isinstance(value, str):
            raise ContentImportError("site option keys and values must be strings")

    return Catalog(
        categories=categories,
        tags=tags,
        articles=articles,
        site_options=site_options,
    )


def build_content_backup(
    *,
    source_root: Path,
    catalog_path: Path,
    schema_path: Path,
    output_archive: Path,
    import_date: date,
    git_commit: str,
    backend_image: str = "umoweb-backend:latest",
    frontend_image: str = "umoweb-frontend:latest",
    asset_roots: list[Path] | None = None,
) -> dict[str, int | str]:
    source_root = source_root.resolve()
    if not source_root.is_dir():
        raise ContentImportError(f"source root is not a directory: {source_root}")
    catalog = load_catalog(catalog_path)
    if not schema_path.is_file():
        raise ContentImportError(f"schema file is missing: {schema_path}")

    with tempfile.TemporaryDirectory(prefix="umoweb-content-import-") as temp:
        staging = Path(temp) / "staging"
        app_data = staging / "app-data"
        staging.mkdir()
        app_data.mkdir()

        article_rows: list[dict[str, Any]] = []
        images_by_path: dict[Path, GeneratedImage] = {}
        images_by_content: dict[tuple[str, str], GeneratedImage] = {}
        slug_by_source = {
            _normalize_relative(article["source"]): article["slug"]
            for article in catalog.articles
        }
        allowed_asset_roots = {
            source_root.parent.resolve(),
            Path.home().resolve(),
        }
        allowed_asset_roots.update(
            root.expanduser().resolve() for root in (asset_roots or [])
        )

        for index, article_spec in enumerate(catalog.articles, start=1):
            relative_source = _normalize_relative(article_spec["source"])
            source_path = (source_root / Path(relative_source)).resolve()
            _assert_within(source_path, source_root)
            if not source_path.is_file():
                raise ContentImportError(f"article source is missing: {relative_source}")
            markdown = source_path.read_text(encoding="utf-8-sig")
            title = derive_title(markdown, source_path.stem)
            rewritten = _rewrite_markdown(
                markdown=markdown,
                source_path=source_path,
                source_root=source_root,
                allowed_asset_roots=allowed_asset_roots,
                slug_by_source=slug_by_source,
                import_date=import_date,
                app_data_root=app_data,
                images_by_path=images_by_path,
                images_by_content=images_by_content,
            )
            body_path = f"contents/NOTE/{article_spec['slug']}.md"
            body_file = app_data / Path(body_path)
            body_file.parent.mkdir(parents=True, exist_ok=True)
            body_file.write_text(rewritten, encoding="utf-8", newline="\n")

            source_timestamp = datetime.fromtimestamp(
                source_path.stat().st_mtime,
                tz=ZoneInfo("Asia/Shanghai"),
            ).replace(tzinfo=None)
            plain_text = _plain_text(rewritten)
            reading_minutes = max(1, (len(plain_text) + 399) // 400)
            article_rows.append(
                {
                    "id": index,
                    "title": title,
                    "slug": article_spec["slug"],
                    "body_path": body_path,
                    "summary": derive_summary(markdown, title),
                    "metadata": json.dumps(
                        {"readingTime": reading_minutes},
                        ensure_ascii=False,
                        separators=(",", ":"),
                    ),
                    "published_at": source_timestamp.strftime("%Y-%m-%d %H:%M:%S"),
                    "category": article_spec["category"],
                    "tags": list(article_spec.get("tags", [])),
                }
            )

        unique_images = {
            image.relative_path: image
            for image in images_by_path.values()
        }
        database_sql = _build_database_sql(
            schema_path=schema_path,
            catalog=catalog,
            article_rows=article_rows,
            images=unique_images.values(),
        )
        (staging / "database.sql").write_text(
            database_sql,
            encoding="utf-8",
            newline="\n",
        )

        _write_inner_archive(app_data, staging / "app-data.tar.gz")
        app_files = _write_app_manifest(app_data, staging / "app-files.tsv")

        now_utc = datetime.now(timezone.utc)
        backup_id = (
            f"umoweb-content-{now_utc.strftime('%Y%m%dT%H%M%SZ')}-{git_commit[:12]}"
        )
        table_counts = {
            "users": 1,
            "categories": len(catalog.categories),
            "tags": len(catalog.tags),
            "contents": len(article_rows),
            "content_category": sum(1 for row in article_rows if row["category"]),
            "content_tag": sum(len(row["tags"]) for row in article_rows),
            "images": len(unique_images),
            "site_options": len(catalog.site_options),
        }
        metadata = _build_metadata(
            backup_id=backup_id,
            git_commit=git_commit,
            table_counts=table_counts,
            app_files=app_files,
            backend_image=backend_image,
            frontend_image=frontend_image,
        )
        (staging / "metadata.env").write_text(
            metadata,
            encoding="utf-8",
            newline="\n",
        )

        checksum_lines = []
        for name in ("database.sql", "app-data.tar.gz", "app-files.tsv", "metadata.env"):
            checksum_lines.append(f"{_sha256_file(staging / name)}  {name}")
        (staging / "SHA256SUMS").write_text(
            "\n".join(checksum_lines) + "\n",
            encoding="utf-8",
            newline="\n",
        )

        output_archive.parent.mkdir(parents=True, exist_ok=True)
        _write_outer_archive(staging, output_archive)
        archive_digest = _sha256_file(output_archive)
        checksum_path = Path(str(output_archive) + ".sha256")
        checksum_path.write_text(
            f"{archive_digest}  {output_archive.name}\n",
            encoding="ascii",
            newline="\n",
        )
        try:
            os.chmod(output_archive, 0o600)
            os.chmod(checksum_path, 0o600)
        except OSError:
            pass

    return {
        "articles": len(article_rows),
        "local_images": len(images_by_path),
        "archive": str(output_archive.resolve()),
        "archive_sha256": archive_digest,
        "backup_id": backup_id,
    }


def _rewrite_markdown(
    *,
    markdown: str,
    source_path: Path,
    source_root: Path,
    allowed_asset_roots: set[Path],
    slug_by_source: dict[str, str],
    import_date: date,
    app_data_root: Path,
    images_by_path: dict[Path, GeneratedImage],
    images_by_content: dict[tuple[str, str], GeneratedImage],
) -> str:
    output_lines: list[str] = []
    in_fence = False
    for line in markdown.splitlines(keepends=True):
        stripped = line.lstrip()
        if stripped.startswith("```") or stripped.startswith("~~~"):
            in_fence = not in_fence
            output_lines.append(line)
            continue
        if in_fence:
            output_lines.append(line)
            continue

        def replace_image(match: re.Match[str]) -> str:
            alt_text = match.group(1)
            raw_target = match.group(2).strip().strip("<>")
            if _is_remote_url(raw_target):
                return match.group(0)
            target_path = _resolve_local_reference(source_path.parent, raw_target)
            if not any(
                _is_within(target_path, allowed_root)
                for allowed_root in allowed_asset_roots
            ):
                raise ContentImportError(
                    f"local image is outside allowed asset roots: {raw_target}"
                )
            if not target_path.is_file():
                raise ContentImportError(
                    f"missing local image: {raw_target} referenced by {source_path}"
                )
            image = images_by_path.get(target_path)
            if image is None:
                image = _register_image(
                    target_path,
                    import_date,
                    app_data_root,
                    images_by_content,
                )
                images_by_path[target_path] = image
            return f"![{alt_text}](/{image.relative_path})"

        rewritten = IMAGE_PATTERN.sub(replace_image, line)

        def replace_link(match: re.Match[str]) -> str:
            label = match.group(1)
            raw_target = match.group(2).strip().strip("<>")
            if _is_remote_url(raw_target) or raw_target.startswith("#"):
                return match.group(0)
            path_part, separator, fragment = raw_target.partition("#")
            target_path = _resolve_local_reference(source_path.parent, path_part)
            if target_path.name == "00_索引.md":
                suffix = f"#{fragment}" if separator else ""
                return f"[{label}](/library{suffix})"
            if target_path.suffix.lower() != ".md":
                return match.group(0)
            try:
                relative_target = target_path.relative_to(source_root).as_posix()
            except ValueError:
                return match.group(0)
            slug = slug_by_source.get(relative_target)
            if slug is None:
                return match.group(0)
            suffix = f"#{fragment}" if separator else ""
            return f"[{label}](/post/{slug}{suffix})"

        output_lines.append(LINK_PATTERN.sub(replace_link, rewritten))
    return "".join(output_lines)


def _register_image(
    path: Path,
    import_date: date,
    app_data_root: Path,
    images_by_content: dict[tuple[str, str], GeneratedImage],
) -> GeneratedImage:
    extension = path.suffix.lower()
    image_type = SUPPORTED_IMAGE_TYPES.get(extension)
    if image_type is None:
        raise ContentImportError(f"unsupported local image type: {path}")
    normalized_extension, content_type = image_type
    data = path.read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    key = (digest, normalized_extension)
    existing = images_by_content.get(key)
    if existing is not None:
        return existing

    stored_name = f"{digest[:24]}.{normalized_extension}"
    relative_path = (
        f"images/{import_date.year:04d}/{import_date.month:02d}/{stored_name}"
    )
    target = app_data_root / Path(relative_path)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
    created_at = datetime.fromtimestamp(
        path.stat().st_mtime,
        tz=ZoneInfo("Asia/Shanghai"),
    ).replace(tzinfo=None)
    image = GeneratedImage(
        original_name=path.name,
        stored_name=stored_name,
        relative_path=relative_path,
        size=len(data),
        content_type=content_type,
        created_at=created_at.strftime("%Y-%m-%d %H:%M:%S"),
    )
    images_by_content[key] = image
    return image


def _build_database_sql(
    *,
    schema_path: Path,
    catalog: Catalog,
    article_rows: list[dict[str, Any]],
    images: Any,
) -> str:
    schema = schema_path.read_text(encoding="utf-8-sig")
    marker = "-- 初始数据: site_options"
    if marker not in schema:
        raise ContentImportError("schema.sql is missing the site_options marker")
    prefix = schema.split(marker, 1)[0].rstrip()
    category_ids = {
        category["key"]: index
        for index, category in enumerate(catalog.categories, start=1)
    }
    tag_ids = {
        tag["key"]: index
        for index, tag in enumerate(catalog.tags, start=1)
    }

    lines = [prefix, "", "SET NAMES utf8mb4;", f"USE {DATABASE_NAME};", ""]
    lines.append(
        "INSERT INTO categories "
        "(id, name, slug, parent_id, type, sort_order) VALUES"
    )
    category_values = []
    for category in catalog.categories:
        parent = category.get("parent")
        parent_sql = "NULL" if parent is None else str(category_ids[parent])
        category_values.append(
            "("
            f"{category_ids[category['key']]}, "
            f"{_sql_string(category['name'])}, "
            f"{_sql_string(category['slug'])}, "
            f"{parent_sql}, "
            f"{_sql_string(category.get('type', 'NOTE'))}, "
            f"{int(category.get('sort_order', 0))}"
            ")"
        )
    lines.append(",\n".join(category_values) + ";")
    lines.append("")

    if catalog.tags:
        lines.append("INSERT INTO tags (id, name, slug) VALUES")
        tag_values = [
            f"({tag_ids[tag['key']]}, {_sql_string(tag['name'])}, {_sql_string(tag['slug'])})"
            for tag in catalog.tags
        ]
        lines.append(",\n".join(tag_values) + ";")
        lines.append("")

    lines.append(
        "INSERT INTO contents "
        "(id, title, slug, body_path, summary, type, status, metadata, "
        "created_at, updated_at, published_at) VALUES"
    )
    content_values = []
    for row in article_rows:
        timestamp = row["published_at"]
        content_values.append(
            "("
            f"{row['id']}, "
            f"{_sql_string(row['title'])}, "
            f"{_sql_string(row['slug'])}, "
            f"{_sql_string(row['body_path'])}, "
            f"{_sql_string(row['summary'])}, "
            "'NOTE', 'PUBLISHED', "
            f"{_sql_string(row['metadata'])}, "
            f"{_sql_string(timestamp)}, {_sql_string(timestamp)}, {_sql_string(timestamp)}"
            ")"
        )
    lines.append(",\n".join(content_values) + ";")
    lines.append("")

    lines.append("INSERT INTO content_category (content_id, category_id) VALUES")
    category_links = [
        f"({row['id']}, {category_ids[row['category']]})"
        for row in article_rows
    ]
    lines.append(",\n".join(category_links) + ";")
    lines.append("")

    tag_links = [
        f"({row['id']}, {tag_ids[tag]})"
        for row in article_rows
        for tag in row["tags"]
    ]
    if tag_links:
        lines.append("INSERT INTO content_tag (content_id, tag_id) VALUES")
        lines.append(",\n".join(tag_links) + ";")
        lines.append("")

    image_list = list(images)
    if image_list:
        lines.append(
            "INSERT INTO images "
            "(id, original_name, stored_name, path, size, content_type, created_at) VALUES"
        )
        image_values = [
            "("
            f"{index}, "
            f"{_sql_string(image.original_name)}, "
            f"{_sql_string(image.stored_name)}, "
            f"{_sql_string(image.relative_path)}, "
            f"{image.size}, "
            f"{_sql_string(image.content_type)}, "
            f"{_sql_string(image.created_at)}"
            ")"
            for index, image in enumerate(image_list, start=1)
        ]
        lines.append(",\n".join(image_values) + ";")
        lines.append("")

    if catalog.site_options:
        lines.append("INSERT INTO site_options (option_key, option_value) VALUES")
        option_values = [
            f"({_sql_string(key)}, {_sql_string(value)})"
            for key, value in catalog.site_options.items()
        ]
        lines.append(",\n".join(option_values) + ";")
        lines.append("")
    return "\n".join(lines)


def _build_metadata(
    *,
    backup_id: str,
    git_commit: str,
    table_counts: dict[str, int],
    app_files: list[tuple[str, int, str]],
    backend_image: str,
    frontend_image: str,
) -> str:
    markdown_count = sum(1 for name, _, _ in app_files if name.endswith(".md"))
    image_count = sum(1 for name, _, _ in app_files if name.startswith("images/"))
    lines = [
        f"BACKUP_ID={backup_id}",
        f"CREATED_AT_UTC={datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')}",
        f"GIT_COMMIT={git_commit}",
        "COMPOSE_PROJECT=umoweb",
        "MYSQL_VERSION=8.4",
        f"MYSQL_DATABASE={DATABASE_NAME}",
        f"BACKUP_HELPER_IMAGE={BACKUP_HELPER_IMAGE}",
        f"BACKEND_IMAGE={backend_image}",
        f"FRONTEND_IMAGE={frontend_image}",
        f"APP_FILE_COUNT={len(app_files)}",
        f"MARKDOWN_FILE_COUNT={markdown_count}",
        f"IMAGE_FILE_COUNT={image_count}",
    ]
    for table, count in table_counts.items():
        lines.append(f"TABLE_COUNT_{table}={count}")
    return "\n".join(lines) + "\n"


def _write_inner_archive(app_data: Path, output: Path) -> None:
    with tarfile.open(output, "w:gz") as archive:
        for path in sorted(app_data.rglob("*")):
            relative = path.relative_to(app_data).as_posix()
            info = archive.gettarinfo(str(path), arcname=relative)
            info.uid = 10001
            info.gid = 10001
            info.uname = "umo"
            info.gname = "umo"
            if path.is_dir():
                info.mode = 0o755
                archive.addfile(info)
            elif path.is_file():
                info.mode = 0o644
                with path.open("rb") as handle:
                    archive.addfile(info, handle)


def _write_app_manifest(
    app_data: Path,
    output: Path,
) -> list[tuple[str, int, str]]:
    rows = []
    for path in sorted(app_data.rglob("*")):
        if not path.is_file():
            continue
        relative = path.relative_to(app_data).as_posix()
        data = path.read_bytes()
        rows.append((relative, len(data), hashlib.sha256(data).hexdigest()))
    output.write_text(
        "".join(f"{name}\t{size}\t{digest}\n" for name, size, digest in rows),
        encoding="utf-8",
        newline="\n",
    )
    return rows


def _write_outer_archive(staging: Path, output: Path) -> None:
    if output.exists():
        output.unlink()
    with tarfile.open(output, "w:gz") as archive:
        for name in (
            "database.sql",
            "app-data.tar.gz",
            "app-files.tsv",
            "metadata.env",
            "SHA256SUMS",
        ):
            archive.add(staging / name, arcname=name)


def _plain_text(value: str) -> str:
    value = re.sub(r"```.*?```", " ", value, flags=re.DOTALL)
    value = IMAGE_PATTERN.sub(" ", value)
    value = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", value)
    value = re.sub(r"<[^>]+>", " ", value)
    value = re.sub(r"[*_~`]", "", value)
    value = re.sub(r"\s+", " ", value)
    return value.strip()


def _truncate(value: str, max_chars: int) -> str:
    if len(value) <= max_chars:
        return value
    return value[: max_chars - 1].rstrip() + "…"


def _resolve_local_reference(parent: Path, raw_target: str) -> Path:
    decoded = unquote(raw_target)
    candidate = Path(decoded)
    if candidate.is_absolute():
        return candidate.resolve()
    return (parent / candidate).resolve()


def _is_remote_url(value: str) -> bool:
    return bool(re.match(r"^(?:https?://|data:|mailto:)", value, flags=re.IGNORECASE))


def _normalize_relative(value: str) -> str:
    path = Path(value.replace("\\", "/"))
    if path.is_absolute() or ".." in path.parts:
        raise ContentImportError(f"source path must be relative and contained: {value}")
    normalized = path.as_posix().lstrip("./")
    if not normalized:
        raise ContentImportError("source path must not be empty")
    return normalized


def _assert_within(path: Path, root: Path) -> None:
    if not _is_within(path, root):
        raise ContentImportError(f"path escapes source root: {path}")


def _is_within(path: Path, root: Path) -> bool:
    try:
        path.relative_to(root)
        return True
    except ValueError:
        return False


def _require_string(value: dict[str, Any], key: str) -> None:
    if not isinstance(value.get(key), str) or not value[key].strip():
        raise ContentImportError(f"catalog field must be a non-empty string: {key}")


def _require_safe_slug(value: Any, kind: str) -> None:
    if not isinstance(value, str) or not SAFE_SLUG.fullmatch(value):
        raise ContentImportError(f"invalid {kind} slug: {value!r}")


def _sql_string(value: str) -> str:
    escaped = value.replace("\\", "\\\\").replace("'", "''")
    return f"'{escaped}'"


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _default_repo_root() -> Path:
    return Path(__file__).resolve().parents[2]


def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build a restorable UmoWeb backup from curated Markdown.",
    )
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--catalog", required=True, type=Path)
    parser.add_argument("--schema", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--import-date", type=date.fromisoformat)
    parser.add_argument("--git-commit", default="unknown")
    parser.add_argument(
        "--asset-root",
        action="append",
        type=Path,
        default=[],
        help="Additional directory allowed to contain local image assets.",
    )
    parser.add_argument("--backend-image", default="umoweb-backend:latest")
    parser.add_argument("--frontend-image", default="umoweb-frontend:latest")
    return parser.parse_args()


def main() -> int:
    args = _parse_args()
    schema = args.schema or (_default_repo_root() / "docs/design/schema.sql")
    import_date = args.import_date or datetime.now(ZoneInfo("Asia/Shanghai")).date()
    try:
        result = build_content_backup(
            source_root=args.source,
            catalog_path=args.catalog,
            schema_path=schema,
            output_archive=args.output,
            import_date=import_date,
            git_commit=args.git_commit,
            backend_image=args.backend_image,
            frontend_image=args.frontend_image,
            asset_roots=args.asset_root,
        )
    except ContentImportError as exc:
        print(f"content import failed: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
