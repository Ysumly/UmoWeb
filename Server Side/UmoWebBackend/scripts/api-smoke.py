#!/usr/bin/env python3
"""Portable UmoWeb API smoke test with the same coverage as api-smoke.ps1."""

from __future__ import annotations

import argparse
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path
from typing import Any


PNG_BYTES = bytes.fromhex(
    "89504E470D0A1A0A0000000D4948445200000001000000010804000000B51C0C02"
    "0000000B4944415478DA63FCFF1F0002EB01F58F59F61F0000000049454E44AE426082"
)


class SmokeFailure(RuntimeError):
    pass


class Smoke:
    def __init__(self, base_url: str, username: str, password: str):
        self.base_url = base_url.rstrip("/")
        self.username = username
        self.password = password
        self.steps = 0
        self.token: str | None = None
        self.category_id: int | None = None
        self.tag_id: int | None = None
        self.content_id: int | None = None

    def run(self) -> None:
        run_id = str(uuid.uuid4())
        try:
            site = self.expect_json("GET", "/api/public/site-info")
            self.require(site.get("siteTitle"), "site-info must contain siteTitle")
            self.step(1, "GET /api/public/site-info")

            about = self.expect_json("GET", "/api/public/pages/about")
            self.require(about.get("content") is not None, "about page must contain content")
            self.step(2, "GET /api/public/pages/about")

            project = self.expect_json("GET", "/api/public/pages/project")
            self.require(
                project.get("content") is not None,
                "project page must contain content",
            )
            self.step(3, "GET /api/public/pages/project")

            categories = self.expect_json("GET", "/api/public/categories?type=NOTE")
            self.require(isinstance(categories, list), "public categories must be an array")
            self.step(4, "GET /api/public/categories")

            tags = self.expect_json("GET", "/api/public/tags")
            self.require(isinstance(tags, list), "public tags must be an array")
            self.step(5, "GET /api/public/tags")

            contents = self.expect_json("GET", "/api/public/contents?page=1&size=10")
            self.require(isinstance(contents.get("items"), list), "public list must have items")
            self.require(
                all(item.get("status") == "PUBLISHED" for item in contents["items"]),
                "public content list must only expose PUBLISHED status",
            )
            self.step(6, "GET /api/public/contents")

            self.request(
                "GET",
                "/api/admin/contents",
                token="invalid",
                expected=401,
            )
            login = self.expect_json(
                "POST",
                "/api/admin/login",
                {"username": self.username, "password": self.password},
            )
            self.require(login.get("token"), "login must return token")
            self.token = login["token"]
            self.step(7, "POST /api/admin/login")

            smoke_password = f"SmokePass{run_id.replace('-', '')}"
            self.request(
                "PUT",
                "/api/admin/change-password",
                {"oldPassword": self.password, "newPassword": smoke_password},
                token=self.token,
                expected=204,
            )
            self.request("GET", "/api/admin/contents", token=self.token, expected=401)
            new_login = self.expect_json(
                "POST",
                "/api/admin/login",
                {"username": self.username, "password": smoke_password},
            )
            new_token = new_login["token"]
            self.request(
                "PUT",
                "/api/admin/change-password",
                {"oldPassword": smoke_password, "newPassword": self.password},
                token=new_token,
                expected=204,
            )
            restored_login = self.expect_json(
                "POST",
                "/api/admin/login",
                {"username": self.username, "password": self.password},
            )
            self.token = restored_login["token"]
            self.step(8, "PUT /api/admin/change-password")

            admin_categories = self.expect_json(
                "GET",
                "/api/admin/categories",
                token=self.token,
            )
            self.require(
                isinstance(admin_categories, list),
                "admin categories must be an array",
            )
            self.step(9, "GET /api/admin/categories")

            category_slug = f"smoke-category-{run_id}"
            category = self.expect_json(
                "POST",
                "/api/admin/categories",
                {
                    "name": f"Smoke Category {run_id}",
                    "slug": category_slug,
                    "type": "NOTE",
                    "sortOrder": 99,
                },
                token=self.token,
            )
            self.category_id = category.get("id")
            self.require(self.category_id, "created category must have id")
            self.step(10, "POST /api/admin/categories")

            category_detail = self.expect_json(
                "GET",
                f"/api/admin/categories/{self.category_id}",
                token=self.token,
            )
            self.require(
                category_detail.get("slug") == category_slug,
                "category detail must match created slug",
            )
            self.step(11, "GET /api/admin/categories/{id}")

            updated_category_slug = f"{category_slug}-updated"
            category_update = self.expect_json(
                "PUT",
                f"/api/admin/categories/{self.category_id}",
                {
                    "name": "Smoke Category Updated",
                    "slug": updated_category_slug,
                    "type": "NOTE",
                    "sortOrder": 100,
                },
                token=self.token,
            )
            self.require(
                category_update.get("slug") == updated_category_slug,
                "category update must change slug",
            )
            self.step(12, "PUT /api/admin/categories/{id}")

            admin_tags = self.expect_json("GET", "/api/admin/tags", token=self.token)
            self.require(isinstance(admin_tags, list), "admin tags must be an array")
            self.step(13, "GET /api/admin/tags")

            tag_slug = f"smoke-tag-{run_id}"
            tag = self.expect_json(
                "POST",
                "/api/admin/tags",
                {"name": f"Smoke Tag {run_id}", "slug": tag_slug},
                token=self.token,
            )
            self.tag_id = tag.get("id")
            self.require(self.tag_id, "created tag must have id")
            self.step(14, "POST /api/admin/tags")

            updated_tag_slug = f"{tag_slug}-updated"
            tag_update = self.expect_json(
                "PUT",
                f"/api/admin/tags/{self.tag_id}",
                {"name": "Smoke Tag Updated", "slug": updated_tag_slug},
                token=self.token,
            )
            self.require(
                tag_update.get("slug") == updated_tag_slug,
                "tag update must change slug",
            )
            self.step(15, "PUT /api/admin/tags/{id}")

            admin_contents = self.expect_json(
                "GET",
                "/api/admin/contents?page=1&size=100",
                token=self.token,
            )
            self.require(
                isinstance(admin_contents.get("items"), list),
                "admin list must have items",
            )
            self.step(16, "GET /api/admin/contents")

            content_slug = f"smoke-content-{run_id}"
            content = self.expect_json(
                "POST",
                "/api/admin/contents",
                {
                    "title": f"Smoke Content {run_id}",
                    "slug": content_slug,
                    "body": "# Smoke\n\nInitial body",
                    "summary": f"Smoke search token {run_id}",
                    "type": "NOTE",
                    "status": "DRAFT",
                    "categoryIds": [self.category_id],
                    "tagIds": [self.tag_id],
                    "metadata": '{"source":"api-smoke"}',
                },
                token=self.token,
            )
            self.content_id = content.get("id")
            self.require(self.content_id, "created content must have id")
            self.require(content.get("status") == "DRAFT", "content must be DRAFT")
            self.step(17, "POST /api/admin/contents")

            self.request(
                "GET",
                f"/api/public/contents/{content_slug}",
                expected=404,
            )
            content_detail = self.expect_json(
                "GET",
                f"/api/admin/contents/{self.content_id}",
                token=self.token,
            )
            self.require(
                content_detail.get("body") == "# Smoke\n\nInitial body",
                "admin detail must load body",
            )
            self.require(
                content_detail.get("status") == "DRAFT",
                "admin detail must expose DRAFT",
            )
            self.step(18, "GET /api/admin/contents/{id}")

            content_update = self.expect_json(
                "PUT",
                f"/api/admin/contents/{self.content_id}",
                {
                    "title": "Smoke Content Updated",
                    "slug": content_slug,
                    "body": "# Smoke\n\nUpdated body",
                    "summary": f"Smoke search token {run_id}",
                    "type": "NOTE",
                    "status": "PUBLISHED",
                    "categoryIds": [self.category_id],
                    "tagIds": [self.tag_id],
                    "metadata": '{"source":"api-smoke","updated":true}',
                },
                token=self.token,
            )
            self.require(
                content_update.get("body") == "# Smoke\n\nUpdated body",
                "content update must persist body",
            )
            self.step(19, "PUT /api/admin/contents/{id}")

            published = self.expect_json(
                "GET",
                "/api/public/contents?page=1&size=100",
            )
            self.require(
                any(item.get("slug") == content_slug for item in published["items"]),
                "published list must contain smoke content",
            )
            public_detail = self.expect_json(
                "GET",
                f"/api/public/contents/{content_slug}",
            )
            self.require(
                public_detail.get("body") == "# Smoke\n\nUpdated body",
                "public detail must load body",
            )
            self.step(20, "GET /api/public/contents/{slug}")

            query = urllib.parse.quote(run_id)
            search = self.expect_json(
                "GET",
                f"/api/public/contents/search?q={query}&page=1&size=10",
            )
            self.require(
                any(item.get("slug") == content_slug for item in search["items"]),
                "search must match smoke content",
            )
            limited = self.expect_json(
                "GET",
                f"/api/public/contents/search?q={query}&page=1&size=10",
                expected=429,
            )
            self.require(limited.get("code") == 429, "limited search must return 429")
            self.step(21, "GET /api/public/contents/search")

            self.request(
                "DELETE",
                f"/api/admin/contents/{self.content_id}",
                token=self.token,
                expected=204,
            )
            self.request(
                "GET",
                f"/api/admin/contents/{self.content_id}",
                token=self.token,
                expected=404,
            )
            self.content_id = None
            self.step(22, "DELETE /api/admin/contents/{id}")

            self.request(
                "DELETE",
                f"/api/admin/tags/{self.tag_id}",
                token=self.token,
                expected=204,
            )
            self.tag_id = None
            self.step(23, "DELETE /api/admin/tags/{id}")

            self.request(
                "DELETE",
                f"/api/admin/categories/{self.category_id}",
                token=self.token,
                expected=204,
            )
            self.category_id = None
            self.step(24, "DELETE /api/admin/categories/{id}")

            image = self.upload_image(run_id)
            self.require(image.get("id"), "uploaded image must have id")
            self.require(
                str(image.get("url", "")).startswith("/images/")
                and str(image["url"]).endswith(".png"),
                "uploaded image must return a public path",
            )
            self.step(25, "POST /api/admin/images/upload")

            options = self.expect_json("GET", "/api/admin/options", token=self.token)
            original_title = options.get("site_title")
            self.require(original_title, "site options must contain site_title")
            self.step(26, "GET /api/admin/options")

            self.request(
                "PUT",
                "/api/admin/options/site_title",
                {"value": "Umo Smoke Title"},
                token=self.token,
                expected=204,
            )
            updated_options = self.expect_json(
                "GET",
                "/api/admin/options",
                token=self.token,
            )
            self.require(
                updated_options.get("site_title") == "Umo Smoke Title",
                "site_title update must persist",
            )
            self.request(
                "PUT",
                "/api/admin/options/site_title",
                {"value": original_title},
                token=self.token,
                expected=204,
            )
            self.step(27, "PUT /api/admin/options/{key}")

            print(
                "API smoke passed: 27/27 endpoints, authentication guard, "
                "draft isolation, password invalidation, and search rate limit."
            )
        finally:
            self.cleanup()

    def cleanup(self) -> None:
        if not self.token:
            return
        for path in (
            f"/api/admin/contents/{self.content_id}" if self.content_id else None,
            f"/api/admin/tags/{self.tag_id}" if self.tag_id else None,
            f"/api/admin/categories/{self.category_id}" if self.category_id else None,
        ):
            if path:
                try:
                    self.request("DELETE", path, token=self.token, expected=None)
                except Exception:
                    pass

    def step(self, number: int, name: str) -> None:
        self.steps += 1
        if self.steps != number:
            raise SmokeFailure(
                f"Smoke step ordering error: expected {number}, got {self.steps}"
            )
        print(f"[{number}/27] PASS {name}")

    def require(self, condition: Any, message: str) -> None:
        if not condition:
            raise SmokeFailure(f"Assertion failed: {message}")

    def expect_json(
        self,
        method: str,
        path: str,
        body: dict[str, Any] | None = None,
        *,
        token: str | None = None,
        expected: int = 200,
    ) -> Any:
        status, content = self.request(
            method,
            path,
            body,
            token=token,
            expected=expected,
        )
        if not content:
            return None
        return json.loads(content)

    def request(
        self,
        method: str,
        path: str,
        body: dict[str, Any] | None = None,
        *,
        token: str | None = None,
        expected: int | None = 200,
    ) -> tuple[int, str]:
        headers = {}
        data = None
        if token:
            headers["Authorization"] = f"Bearer {token}"
        if body is not None:
            headers["Content-Type"] = "application/json"
            data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        request = urllib.request.Request(
            self.base_url + path,
            data=data,
            method=method,
            headers=headers,
        )
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                status = response.status
                content = response.read().decode("utf-8")
        except urllib.error.HTTPError as exc:
            status = exc.code
            content = exc.read().decode("utf-8")
        if expected is not None and status != expected:
            raise SmokeFailure(
                f"{method} {path} expected HTTP {expected} but returned "
                f"{status}: {content}"
            )
        return status, content

    def upload_image(self, run_id: str) -> dict[str, Any]:
        boundary = f"----umoweb-smoke-{run_id}"
        body = (
            f"--{boundary}\r\n"
            'Content-Disposition: form-data; name="file"; filename="smoke.png"\r\n'
            "Content-Type: image/png\r\n\r\n"
        ).encode("ascii") + PNG_BYTES + f"\r\n--{boundary}--\r\n".encode("ascii")
        request = urllib.request.Request(
            self.base_url + "/api/admin/images/upload",
            data=body,
            method="POST",
            headers={
                "Authorization": f"Bearer {self.token}",
                "Content-Type": f"multipart/form-data; boundary={boundary}",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                if response.status != 200:
                    raise SmokeFailure(
                        f"image upload returned HTTP {response.status}"
                    )
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            raise SmokeFailure(
                f"image upload returned HTTP {exc.code}: "
                f"{exc.read().decode('utf-8')}"
            ) from exc


def read_env_value(path: Path, key: str) -> str:
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.startswith(key + "="):
            return line.split("=", 1)[1]
    raise SmokeFailure(f"{key} is missing from {path}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--username")
    parser.add_argument("--password")
    parser.add_argument("--env-file", type=Path)
    parser.add_argument("--env-key", default="INIT_ADMIN_PASS")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    password = args.password
    env_path = args.env_file
    if not password:
        env_path = env_path or Path(
            os.environ.get("COMPOSE_ENV_FILE", ".env.docker")
        )
        password = read_env_value(env_path, args.env_key)
    username = args.username
    if not username and env_path:
        username = read_env_value(env_path, "INIT_ADMIN_USER")
    username = username or "admin"
    try:
        Smoke(args.base_url, username, password).run()
    except SmokeFailure as exc:
        print(f"API smoke failed: {exc}")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
