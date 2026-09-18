-- UmoWeb admin AI mode catalog migration for existing MySQL databases.
-- Back up the database before running this script.

USE umo_blog;

CREATE TABLE IF NOT EXISTS ai_transform_modes (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    mode_key        VARCHAR(64)  NOT NULL UNIQUE,
    name            VARCHAR(100) NOT NULL,
    description     VARCHAR(500) NOT NULL DEFAULT '',
    enabled         TINYINT(1)   NOT NULL DEFAULT 0,
    sort_order      INT          NOT NULL DEFAULT 0,
    current_version INT          NOT NULL,
    created_at      DATETIME     NOT NULL DEFAULT NOW(),
    updated_at      DATETIME     NOT NULL DEFAULT NOW() ON UPDATE NOW(),
    INDEX idx_ai_transform_modes_enabled_sort (enabled, sort_order, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_transform_mode_versions (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    mode_id            BIGINT       NOT NULL,
    version_no         INT          NOT NULL,
    system_prompt      MEDIUMTEXT   NOT NULL,
    validation_profile VARCHAR(32)  NOT NULL,
    created_at         DATETIME     NOT NULL DEFAULT NOW(),
    UNIQUE KEY uk_ai_mode_version (mode_id, version_no),
    CONSTRAINT fk_ai_mode_version_mode
        FOREIGN KEY (mode_id) REFERENCES ai_transform_modes(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
