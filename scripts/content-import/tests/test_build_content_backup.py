import json
import re
import sys
import tarfile
import tempfile
import unittest
from datetime import date
from pathlib import Path

MODULE_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(MODULE_DIR))

import build_content_backup as subject


MINIMAL_SCHEMA = """\
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS umo_blog
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE umo_blog;

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    token_version INT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT NOW(),
    updated_at DATETIME NOT NULL DEFAULT NOW()
);
CREATE TABLE categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(100) NOT NULL UNIQUE,
    parent_id BIGINT NULL,
    type VARCHAR(30) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT NOW(),
    updated_at DATETIME NOT NULL DEFAULT NOW()
);
CREATE TABLE tags (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(100) NOT NULL UNIQUE,
    created_at DATETIME NOT NULL DEFAULT NOW()
);
CREATE TABLE contents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    slug VARCHAR(500) NOT NULL UNIQUE,
    body_path VARCHAR(500) NOT NULL,
    summary VARCHAR(2000) NULL,
    type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    metadata JSON NULL,
    created_at DATETIME NOT NULL DEFAULT NOW(),
    updated_at DATETIME NOT NULL DEFAULT NOW(),
    published_at DATETIME NULL
);
CREATE TABLE content_category (
    content_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL
);
CREATE TABLE content_tag (
    content_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL
);
CREATE TABLE images (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    original_name VARCHAR(500) NOT NULL,
    stored_name VARCHAR(255) NOT NULL,
    path VARCHAR(500) NOT NULL,
    size BIGINT NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    width INT NULL,
    height INT NULL,
    created_at DATETIME NOT NULL DEFAULT NOW()
);
CREATE TABLE site_options (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    option_key VARCHAR(100) NOT NULL UNIQUE,
    option_value TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT NOW(),
    updated_at DATETIME NOT NULL DEFAULT NOW()
);

-- 初始数据: site_options
INSERT INTO site_options (option_key, option_value) VALUES
('site_title', 'Demo');
"""


class DeriveTextTests(unittest.TestCase):
    def test_derive_title_uses_first_h1_then_filename(self):
        self.assertEqual(subject.derive_title("# 常见算法\n\n正文", "fallback"), "常见算法")
        self.assertEqual(subject.derive_title("## 小标题\n\n正文", "PlayerPrefs"), "PlayerPrefs")

    def test_derive_summary_skips_headings_and_returns_plain_text(self):
        markdown = """\
# MySQL 基础

## 本单元目标

学习 **数据库**、[SQL](https://example.com) 和 `SELECT`。

![图片](image.png)
"""

        self.assertEqual(
            subject.derive_summary(markdown, "MySQL 基础"),
            "学习 数据库、SQL 和 SELECT。",
        )

    def test_derive_summary_skips_horizontal_rules(self):
        markdown = """\
# 数据导向设计

---

数据导向设计关注数据在内存中的布局。
"""

        self.assertEqual(
            subject.derive_summary(markdown, "数据导向设计"),
            "数据导向设计关注数据在内存中的布局。",
        )


class BuildContentBackupTests(unittest.TestCase):
    def test_deduplicates_identical_image_content(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "source"
            category_dir = source / "01_计算机基础"
            category_dir.mkdir(parents=True)
            (category_dir / "first.png").write_bytes(b"same-image")
            (category_dir / "second.png").write_bytes(b"same-image")
            (category_dir / "article.md").write_text(
                "# Article\n\n![one](first.png)\n\n![two](second.png)\n",
                encoding="utf-8",
            )
            catalog_path = root / "catalog.json"
            catalog_path.write_text(
                json.dumps(
                    {
                        "categories": [
                            {
                                "key": "computer-basics",
                                "name": "计算机基础",
                                "slug": "computer-basics",
                                "parent": None,
                                "sort_order": 1,
                            }
                        ],
                        "tags": [],
                        "articles": [
                            {
                                "source": "01_计算机基础/article.md",
                                "slug": "article",
                                "category": "computer-basics",
                                "tags": [],
                            }
                        ],
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
            schema_path = root / "schema.sql"
            schema_path.write_text(MINIMAL_SCHEMA, encoding="utf-8")
            archive = root / "candidate.tar.gz"

            result = subject.build_content_backup(
                source_root=source,
                catalog_path=catalog_path,
                schema_path=schema_path,
                output_archive=archive,
                import_date=date(2026, 9, 12),
                git_commit="abc123",
            )

            self.assertEqual(result["local_images"], 2)
            with tarfile.open(archive, "r:gz") as outer:
                database_sql = outer.extractfile("database.sql").read().decode("utf-8")
                metadata = outer.extractfile("metadata.env").read().decode("utf-8")
                inner_archive = outer.extractfile("app-data.tar.gz")
                with tarfile.open(fileobj=inner_archive, mode="r:gz") as app_data:
                    body = app_data.extractfile("contents/NOTE/article.md").read().decode("utf-8")

            self.assertEqual(len([line for line in body.splitlines() if "![" in line]), 2)
            self.assertEqual(len(set(re.findall(r"\]\((/images/[^)]+)\)", body))), 1)
            self.assertIn("TABLE_COUNT_images=1", metadata)
            self.assertEqual(database_sql.count("INSERT INTO images"), 1)

    def test_builds_restorable_backup_with_rewritten_content_and_images(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "source"
            article_dir = source / "01_计算机基础" / "01_数据结构与算法"
            article_dir.mkdir(parents=True)
            image = article_dir / "diagram.png"
            image.write_bytes(b"\x89PNG\r\n\x1a\ncontent")
            article = article_dir / "常见算法解析.md"
            article.write_text(
                "# 常见算法解析\n\n"
                "本文整理常见算法。\n\n"
                "![算法图](diagram.png)\n\n"
                "[相关笔记](../../02_Java开发/01_设计模式/Java设计模式.md)\n",
                encoding="utf-8",
            )
            second_dir = source / "02_Java开发" / "01_设计模式"
            second_dir.mkdir(parents=True)
            (second_dir / "Java设计模式.md").write_text(
                "# Java 设计模式\n\n整理设计模式。\n",
                encoding="utf-8",
            )

            catalog = {
                "site_options": {
                    "site_title": "Umo Blog",
                    "site_subtitle": "代码 · 游戏开发 · 学习笔记",
                    "about_page": "# 关于本站\n\n学习笔记索引。",
                    "project_page": "# 项目\n\nUmoWeb。",
                },
                "categories": [
                    {
                        "key": "computer-basics",
                        "name": "计算机基础",
                        "slug": "computer-basics",
                        "parent": None,
                        "sort_order": 1,
                    },
                    {
                        "key": "algorithms",
                        "name": "数据结构与算法",
                        "slug": "data-structures-algorithms",
                        "parent": "computer-basics",
                        "sort_order": 1,
                    },
                    {
                        "key": "java-development",
                        "name": "Java 开发",
                        "slug": "java-development",
                        "parent": None,
                        "sort_order": 2,
                    },
                    {
                        "key": "design-patterns",
                        "name": "设计模式",
                        "slug": "design-patterns",
                        "parent": "java-development",
                        "sort_order": 1,
                    },
                ],
                "tags": [
                    {"key": "algorithm", "name": "算法", "slug": "algorithm"},
                    {"key": "java", "name": "Java", "slug": "java"},
                ],
                "articles": [
                    {
                        "source": "01_计算机基础/01_数据结构与算法/常见算法解析.md",
                        "slug": "common-algorithms",
                        "category": "algorithms",
                        "tags": ["algorithm"],
                    },
                    {
                        "source": "02_Java开发/01_设计模式/Java设计模式.md",
                        "slug": "java-design-patterns",
                        "category": "design-patterns",
                        "tags": ["java"],
                    },
                ],
            }
            catalog_path = root / "catalog.json"
            catalog_path.write_text(json.dumps(catalog, ensure_ascii=False), encoding="utf-8")
            schema_path = root / "schema.sql"
            schema_path.write_text(MINIMAL_SCHEMA, encoding="utf-8")
            archive = root / "candidate.tar.gz"

            result = subject.build_content_backup(
                source_root=source,
                catalog_path=catalog_path,
                schema_path=schema_path,
                output_archive=archive,
                import_date=date(2026, 9, 12),
                git_commit="abc123",
            )

            self.assertTrue(archive.is_file())
            self.assertTrue(Path(str(archive) + ".sha256").is_file())
            self.assertEqual(result["articles"], 2)
            self.assertEqual(result["local_images"], 1)

            with tarfile.open(archive, "r:gz") as outer:
                names = set(outer.getnames())
                self.assertEqual(
                    names,
                    {
                        "database.sql",
                        "app-data.tar.gz",
                        "app-files.tsv",
                        "metadata.env",
                        "SHA256SUMS",
                    },
                )
                database_sql = outer.extractfile("database.sql").read().decode("utf-8")
                metadata = outer.extractfile("metadata.env").read().decode("utf-8")
                app_files = outer.extractfile("app-files.tsv").read().decode("utf-8")
                inner_archive = outer.extractfile("app-data.tar.gz")
                with tarfile.open(fileobj=inner_archive, mode="r:gz") as app_data:
                    markdown_name = (
                        "contents/NOTE/common-algorithms.md"
                    )
                    markdown_member = app_data.getmember(markdown_name)
                    note_directory = app_data.getmember("contents/NOTE")
                    markdown = app_data.extractfile(markdown_name).read().decode("utf-8")
                    image_names = [
                        name
                        for name in app_data.getnames()
                        if name.startswith("images/2026/09/")
                    ]

            self.assertIn("/images/2026/09/", markdown)
            self.assertIn("/post/java-design-patterns", markdown)
            self.assertNotIn("diagram.png", markdown)
            self.assertEqual(len(image_names), 1)
            self.assertEqual(markdown_member.uid, 10001)
            self.assertEqual(markdown_member.gid, 10001)
            self.assertEqual(markdown_member.uname, "umo")
            self.assertEqual(markdown_member.gname, "umo")
            self.assertEqual(note_directory.uid, 10001)
            self.assertEqual(note_directory.gid, 10001)
            self.assertIn("common-algorithms.md", app_files)
            self.assertIn("TABLE_COUNT_users=1", metadata)
            self.assertIn("TABLE_COUNT_contents=2", metadata)
            self.assertIn("TABLE_COUNT_images=1", metadata)
            self.assertNotIn("\r", metadata)
            self.assertIn("INSERT INTO contents", database_sql)
            self.assertIn("common-algorithms", database_sql)
            self.assertNotIn(str(source), database_sql)

    def test_rejects_missing_local_image(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "source"
            article_dir = source / "01_计算机基础"
            article_dir.mkdir(parents=True)
            (article_dir / "article.md").write_text(
                "# Article\n\n![missing](missing.png)\n",
                encoding="utf-8",
            )
            catalog_path = root / "catalog.json"
            catalog_path.write_text(
                json.dumps(
                    {
                        "categories": [
                            {
                                "key": "computer-basics",
                                "name": "计算机基础",
                                "slug": "computer-basics",
                                "parent": None,
                                "sort_order": 1,
                            }
                        ],
                        "tags": [],
                        "articles": [
                            {
                                "source": "01_计算机基础/article.md",
                                "slug": "article",
                                "category": "computer-basics",
                                "tags": [],
                            }
                        ],
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
            schema_path = root / "schema.sql"
            schema_path.write_text(MINIMAL_SCHEMA, encoding="utf-8")

            with self.assertRaisesRegex(
                subject.ContentImportError,
                "missing local image",
            ):
                subject.build_content_backup(
                    source_root=source,
                    catalog_path=catalog_path,
                    schema_path=schema_path,
                    output_archive=root / "candidate.tar.gz",
                    import_date=date(2026, 9, 12),
                    git_commit="abc123",
                )


if __name__ == "__main__":
    unittest.main()
