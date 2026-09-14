-- UmoWeb image cleanup queue migration for existing MySQL databases.
-- Back up the database before running this script.

USE umo_blog;

CREATE TABLE IF NOT EXISTS image_cleanup_queue (
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
