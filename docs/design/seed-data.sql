-- ============================================================
-- UmoWeb 种子数据 — 前端开发 & 接口测试用
-- 使用前确保已执行过 schema.sql 建表
-- 注意: 种子记录只写 contents.body_path。
-- 如未在 app.storage-path 下创建对应 Markdown 文件，详情接口仍返回 200，但 body 为空字符串。
-- 管理员账号由后端 DataInitializer 自动创建，不在本脚本中写入明文或固定 BCrypt hash。
-- ============================================================

SET NAMES utf8mb4;

USE umo_blog;

-- 清空旧数据 (可选，按需执行)
-- SET FOREIGN_KEY_CHECKS = 0;
-- TRUNCATE content_tag;
-- TRUNCATE content_category;
-- TRUNCATE contents;
-- TRUNCATE tags;
-- TRUNCATE categories;
-- SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 1. 分类 (层级结构)
-- ============================================================

INSERT INTO categories (id, name, slug, parent_id, type, sort_order) VALUES
-- NOTE 分类
(1,  '编程',      'programming', NULL, 'NOTE', 1),
(2,  'Java',      'java',        1,    'NOTE', 1),
(3,  'Spring',    'spring',      1,    'NOTE', 2),
(4,  '数据库',    'database',    1,    'NOTE', 3),
(5,  '前端',      'frontend',    NULL, 'NOTE', 2),
(6,  'JavaScript','javascript',  5,    'NOTE', 1),
(7,  'Vue',       'vue',         5,    'NOTE', 2),
-- NOVEL 分类
(8,  '小说',      'novels',      NULL, 'NOVEL', 1),
(9,  '凡人修仙传', 'fanren',      8,    'NOVEL', 1),
-- BOOK_REVIEW 分类
(10, '读后感',    'reviews',     NULL, 'BOOK_REVIEW', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- ============================================================
-- 2. 标签
-- ============================================================

INSERT INTO tags (id, name, slug) VALUES
(1, 'Java',     'java'),
(2, 'Spring',   'spring'),
(3, 'MySQL',    'mysql'),
(4, 'Vue',      'vue'),
(5, '前端',     'frontend'),
(6, '笔记',     'note'),
(7, '教程',     'tutorial'),
(8, '修仙',     'xiuxian'),
(9, '读后感',   'review')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- ============================================================
-- 3. 文章 (PUBLISHED — 公开端可见)
-- ============================================================

INSERT INTO contents (id, title, slug, body_path, summary, type, status, metadata, published_at) VALUES
-- NOTE: Spring 入门
(1,
 'Spring Boot 快速上手',
 'spring-boot-quickstart',
 'contents/NOTE/spring-boot-quickstart.md',
 '从零搭建一个 Spring Boot 项目，涵盖基础配置、依赖注入、REST API。',
 'NOTE',
 'PUBLISHED',
 '{"readingTime": 10, "difficulty": "beginner"}',
 '2026-06-20 10:00:00'),

-- NOTE: Java 集合
(2,
 'Java 集合框架详解',
 'java-collections',
 'contents/NOTE/java-collections.md',
 '深入理解 List、Set、Map 及其实现类的底层原理和适用场景。',
 'NOTE',
 'PUBLISHED',
 '{"readingTime": 25, "difficulty": "advanced"}',
 '2026-06-22 14:30:00'),

-- NOTE: Vue 入门
(3,
 'Vue 3 Composition API 入门',
 'vue3-composition-api',
 'contents/NOTE/vue3-composition-api.md',
 'Vue 3 组合式 API 核心概念：ref、reactive、computed、watch。',
 'NOTE',
 'PUBLISHED',
 '{"readingTime": 15, "difficulty": "intermediate"}',
 '2026-06-24 09:00:00'),

-- BOOK_REVIEW
(4,
 '读《代码整洁之道》',
 'clean-code-review',
 'contents/BOOK_REVIEW/clean-code-review.md',
 'Robert C. Martin 的经典之作，读完对代码质量有了全新的认识。',
 'BOOK_REVIEW',
 'PUBLISHED',
 '{"bookTitle": "代码整洁之道", "author": "Robert C. Martin", "rating": 5}',
 '2026-06-25 16:00:00'),

-- NOVEL 第一章
(5,
 '第一章 山村少年',
 'fanren-chapter-001',
 'contents/NOVEL/fanren/chapter-001.md',
 '一个平凡的山村少年，意外踏上了修仙之路……',
 'NOVEL',
 'PUBLISHED',
 '{"volume": 1, "chapter": 1, "wordCount": 3200}',
 '2026-06-26 08:00:00'),

-- DRAFT — 管理端可见，公开端不可见
(6,
 'MySQL 索引优化笔记 (草稿)',
 'mysql-index-draft',
 'contents/NOTE/mysql-index-draft.md',
 '还在整理中的 MySQL 索引优化笔记……',
 'NOTE',
 'DRAFT',
 '{"readingTime": 20, "difficulty": "advanced"}',
 NULL)

ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    slug = VALUES(slug),
    body_path = VALUES(body_path),
    summary = VALUES(summary),
    type = VALUES(type),
    status = VALUES(status),
    metadata = VALUES(metadata),
    published_at = VALUES(published_at);

-- ============================================================
-- 4. 文章 ↔ 分类关联
-- ============================================================

INSERT IGNORE INTO content_category (content_id, category_id) VALUES
(1, 2),  -- Spring Boot → Java
(1, 3),  -- Spring Boot → Spring
(2, 2),  -- Java 集合 → Java
(3, 6),  -- Vue 3 → JavaScript
(3, 7),  -- Vue 3 → Vue
(4, 10), -- 读后感 → 读后感分类
(5, 9),  -- 第一章 → 凡人修仙传
(5, 8),  -- 第一章 → 小说
(6, 2);  -- MySQL 索引 → Java (草稿)

-- ============================================================
-- 5. 文章 ↔ 标签关联
-- ============================================================

INSERT IGNORE INTO content_tag (content_id, tag_id) VALUES
(1, 1),  -- Spring Boot → Java
(1, 2),  -- Spring Boot → Spring
(1, 7),  -- Spring Boot → 教程
(2, 1),  -- Java 集合 → Java
(2, 6),  -- Java 集合 → 笔记
(3, 4),  -- Vue 3 → Vue
(3, 5),  -- Vue 3 → 前端
(3, 7),  -- Vue 3 → 教程
(4, 9),  -- 读后感 → 读后感
(5, 8),  -- 第一章 → 修仙
(6, 1),  -- MySQL 索引 → Java
(6, 3);  -- MySQL 索引 → MySQL

-- ============================================================
-- 补充: site_options 覆盖 (如果 schema.sql 已初始过可跳过)
-- ============================================================

INSERT INTO site_options (option_key, option_value) VALUES
('site_title',    'Umo Blog'),
('site_subtitle', '代码 · 阅读 · 创作'),
('about_page',    '## 关于我\n\n一个热爱编程和写作的开发者。\n\n- Java / Spring 后端开发\n- Vue 前端\n- 偶尔写小说'),
('project_page',  '## 项目\n\n| 项目 | 说明 |\n|---|---|\n| UmoWeb | 本博客系统 |\n| XianXiaGame | 仙侠回合制游戏 (开发中) |')
ON DUPLICATE KEY UPDATE option_value = VALUES(option_value);

-- ============================================================
-- 6. AI 转换模式 (默认停用)
-- ============================================================

INSERT IGNORE INTO ai_transform_modes
    (mode_key, name, description, enabled, sort_order, current_version)
VALUES
    (
        'STRUCTURE_CLEANUP',
        '结构整理',
        '在不改写正文的前提下整理 Markdown 结构与层次。',
        0,
        1,
        1
    ),
    (
        'MODERN_TO_CLASSICAL',
        '现代文转文言文',
        '将现代中文改写为保留原意的文言文。',
        0,
        2,
        1
    ),
    (
        'ENGLISH_TO_CHINESE',
        '英译中',
        '将英文内容忠实翻译为中文。',
        0,
        3,
        1
    ),
    (
        'CHINESE_TO_ENGLISH',
        '中译英',
        '将中文内容忠实翻译为英文。',
        0,
        4,
        1
    ),
    (
        'LIGHT_NOVELIZATION',
        '轻度小说化',
        '在不新增关键事实的前提下轻度增强叙事描写。',
        0,
        5,
        1
    );

INSERT IGNORE INTO ai_transform_mode_versions
    (mode_id, version_no, system_prompt, validation_profile)
SELECT id, 1,
       '你是 Markdown 结构整理器。只允许分段、归类、排序和添加标题。不得增加、删除或改写正文文字、数字、链接、代码、引用和列表项。保留全部 Markdown 语法。只输出整理后的 Markdown。',
       'EXACT_CONTENT'
FROM ai_transform_modes
WHERE mode_key = 'STRUCTURE_CLEANUP'
  AND current_version = 1
  AND NOT EXISTS (
      SELECT 1 FROM ai_transform_mode_versions WHERE mode_id = ai_transform_modes.id
  );

INSERT IGNORE INTO ai_transform_mode_versions
    (mode_id, version_no, system_prompt, validation_profile)
SELECT id, 1,
       '你是现代中文转文言文的编辑。保留原文事实、人物、数字、专有名词、段落顺序和 Markdown 结构。语言应古雅但仍可理解，不添加原文没有的信息。只输出转换后的 Markdown。',
       'NONE'
FROM ai_transform_modes
WHERE mode_key = 'MODERN_TO_CLASSICAL'
  AND current_version = 1
  AND NOT EXISTS (
      SELECT 1 FROM ai_transform_mode_versions WHERE mode_id = ai_transform_modes.id
  );

INSERT IGNORE INTO ai_transform_mode_versions
    (mode_id, version_no, system_prompt, validation_profile)
SELECT id, 1,
       '你是英译中翻译器。忠实保留事实、数字、专有名词、语气和 Markdown 结构。链接、代码、变量和命令不翻译。只输出中文译文，不解释翻译过程。',
       'TRANSLATION'
FROM ai_transform_modes
WHERE mode_key = 'ENGLISH_TO_CHINESE'
  AND current_version = 1
  AND NOT EXISTS (
      SELECT 1 FROM ai_transform_mode_versions WHERE mode_id = ai_transform_modes.id
  );

INSERT IGNORE INTO ai_transform_mode_versions
    (mode_id, version_no, system_prompt, validation_profile)
SELECT id, 1,
       '你是中译英翻译器。忠实保留事实、数字、专有名词、语气和 Markdown 结构。链接、代码、变量和命令不翻译。只输出英文译文，不解释翻译过程。',
       'TRANSLATION'
FROM ai_transform_modes
WHERE mode_key = 'CHINESE_TO_ENGLISH'
  AND current_version = 1
  AND NOT EXISTS (
      SELECT 1 FROM ai_transform_mode_versions WHERE mode_id = ai_transform_modes.id
  );

INSERT IGNORE INTO ai_transform_mode_versions
    (mode_id, version_no, system_prompt, validation_profile)
SELECT id, 1,
       '你是轻度小说化润色器。只增强节奏、意象、动作和感官描写，不新增关键事件、人物、事实或结局。输出不超过原文 1.5 倍，并明确这是文学化改写而非等价转换。只输出 Markdown。',
       'LIGHT_EXPANSION'
FROM ai_transform_modes
WHERE mode_key = 'LIGHT_NOVELIZATION'
  AND current_version = 1
  AND NOT EXISTS (
      SELECT 1 FROM ai_transform_mode_versions WHERE mode_id = ai_transform_modes.id
  );
