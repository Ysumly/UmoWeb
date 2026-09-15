-- ============================================================
-- UmoWeb 个人博客系统 — 数据库建表脚本
-- 版本: 1.1 | 日期: 2026-09-11
-- 对应文档: docs/design/architecture-design.md
-- 外键与级联策略由数据库约束兜底，Service 仍负责返回可读的 409。
-- 管理员账号不在本脚本中预置，由后端 DataInitializer 在 users 为空时创建。
-- ============================================================

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS umo_blog
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE umo_blog;

-- -----------------------------------------------------------
-- 1. users — 管理员用户
-- -----------------------------------------------------------
CREATE TABLE users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    token_version INT          NOT NULL DEFAULT 1,
    created_at    DATETIME     NOT NULL DEFAULT NOW(),
    updated_at    DATETIME     NOT NULL DEFAULT NOW() ON UPDATE NOW()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------
-- 2. categories — 统一分类层级
-- -----------------------------------------------------------
CREATE TABLE categories (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    slug       VARCHAR(100) NOT NULL UNIQUE,
    parent_id  BIGINT       NULL,
    type       VARCHAR(30)  NOT NULL COMMENT 'NOTE / NOVEL / BOOK_REVIEW',
    sort_order INT          NOT NULL DEFAULT 0,
    created_at DATETIME     NOT NULL DEFAULT NOW(),
    updated_at DATETIME     NOT NULL DEFAULT NOW() ON UPDATE NOW(),
    INDEX idx_parent_id (parent_id),
    INDEX idx_type (type),
    CONSTRAINT fk_categories_parent
        FOREIGN KEY (parent_id) REFERENCES categories(id)
        ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------
-- 3. tags — 标签
-- -----------------------------------------------------------
CREATE TABLE tags (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    slug       VARCHAR(100) NOT NULL UNIQUE,
    created_at DATETIME     NOT NULL DEFAULT NOW()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------
-- 4. contents — 文本类内容
-- -----------------------------------------------------------
CREATE TABLE contents (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    title        VARCHAR(500)  NOT NULL,
    slug         VARCHAR(500)  NOT NULL UNIQUE,
    body_path    VARCHAR(500)  NOT NULL COMMENT 'MD 文件相对路径',
    summary      VARCHAR(2000) NULL,
    type         VARCHAR(30)   NOT NULL COMMENT 'NOTE / NOVEL / BOOK_REVIEW',
    status       VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / SCHEDULED / PUBLISHED / ARCHIVED',
    metadata     JSON          NULL COMMENT '各类型专属扩展字段',
    created_at   DATETIME      NOT NULL DEFAULT NOW(),
    updated_at   DATETIME      NOT NULL DEFAULT NOW() ON UPDATE NOW(),
    published_at DATETIME      NULL COMMENT '首次发布时间',
    scheduled_at DATETIME(6)   NULL COMMENT '计划发布时间',
    INDEX idx_type (type),
    INDEX idx_status (status),
    INDEX idx_type_status (type, status),
    INDEX idx_published_at (published_at),
    INDEX idx_status_scheduled_at (status, scheduled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------
-- 5. content_category — 内容↔分类 多对多
-- -----------------------------------------------------------
CREATE TABLE content_category (
    content_id  BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    UNIQUE KEY uk_content_category (content_id, category_id),
    INDEX idx_content_category_category_id (category_id),
    CONSTRAINT fk_content_category_content
        FOREIGN KEY (content_id) REFERENCES contents(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_content_category_category
        FOREIGN KEY (category_id) REFERENCES categories(id)
        ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------
-- 6. content_tag — 内容↔标签 多对多
-- -----------------------------------------------------------
CREATE TABLE content_tag (
    content_id BIGINT NOT NULL,
    tag_id     BIGINT NOT NULL,
    UNIQUE KEY uk_content_tag (content_id, tag_id),
    INDEX idx_content_tag_tag_id (tag_id),
    CONSTRAINT fk_content_tag_content
        FOREIGN KEY (content_id) REFERENCES contents(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_content_tag_tag
        FOREIGN KEY (tag_id) REFERENCES tags(id)
        ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------
-- 7. images — 上传图片记录
-- -----------------------------------------------------------
CREATE TABLE images (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    original_name VARCHAR(500) NOT NULL,
    stored_name   VARCHAR(255) NOT NULL,
    path          VARCHAR(500) NOT NULL,
    size          BIGINT       NOT NULL,
    content_type  VARCHAR(50)  NOT NULL,
    width         INT          NULL,
    height        INT          NULL,
    created_at    DATETIME     NOT NULL DEFAULT NOW()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------
-- 8. image_cleanup_queue — 图片文件待清理队列
-- -----------------------------------------------------------
CREATE TABLE image_cleanup_queue (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    image_id    BIGINT       NOT NULL,
    path        VARCHAR(500) NOT NULL,
    attempts    INT          NOT NULL DEFAULT 0,
    last_error  VARCHAR(1000) NULL,
    created_at  DATETIME     NOT NULL DEFAULT NOW(),
    updated_at  DATETIME     NOT NULL DEFAULT NOW() ON UPDATE NOW(),
    UNIQUE KEY uk_image_cleanup_path (path),
    INDEX idx_image_cleanup_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------
-- 9. content_search — Markdown 正文全文索引
-- -----------------------------------------------------------
CREATE TABLE content_search (
    content_id BIGINT   NOT NULL PRIMARY KEY,
    body_text  LONGTEXT NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT NOW() ON UPDATE NOW(),
    FULLTEXT KEY ft_content_search_body (body_text) WITH PARSER ngram,
    CONSTRAINT fk_content_search_content
        FOREIGN KEY (content_id) REFERENCES contents(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------
-- 10. site_options — 站点配置 KV
-- -----------------------------------------------------------
CREATE TABLE site_options (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    option_key   VARCHAR(100) NOT NULL UNIQUE,
    option_value TEXT         NOT NULL,
    created_at   DATETIME     NOT NULL DEFAULT NOW(),
    updated_at   DATETIME     NOT NULL DEFAULT NOW() ON UPDATE NOW()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------
-- 初始数据: site_options
-- -----------------------------------------------------------
INSERT INTO site_options (option_key, option_value) VALUES
('site_title',    'My Blog'),
('site_subtitle', 'A personal portfolio blog'),
('about_page',    '## About Me\n\nWrite your introduction here.'),
('project_page',  '## Projects\n\nList your projects here.');
