-- UmoWeb full-text content search migration for MySQL 8.4.
-- Back up the database before running this script.

USE umo_blog;

CREATE TABLE IF NOT EXISTS content_search (
    content_id BIGINT       NOT NULL PRIMARY KEY,
    body_text  LONGTEXT     NOT NULL,
    updated_at DATETIME     NOT NULL DEFAULT NOW() ON UPDATE NOW(),
    FULLTEXT KEY ft_content_search_body (body_text) WITH PARSER ngram,
    CONSTRAINT fk_content_search_content
        FOREIGN KEY (content_id) REFERENCES contents(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
