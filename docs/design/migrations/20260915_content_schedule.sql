-- UmoWeb scheduled publishing migration for existing MySQL databases.
-- Back up the database before running this script.

USE umo_blog;

DELIMITER //

DROP PROCEDURE IF EXISTS add_scheduled_at_if_missing//
CREATE PROCEDURE add_scheduled_at_if_missing()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'contents'
          AND column_name = 'scheduled_at'
    ) THEN
        ALTER TABLE contents
            ADD COLUMN scheduled_at DATETIME(6) NULL COMMENT '计划发布时间'
            AFTER published_at;
    END IF;
END//

DROP PROCEDURE IF EXISTS add_schedule_index_if_missing//
CREATE PROCEDURE add_schedule_index_if_missing()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'contents'
          AND index_name = 'idx_status_scheduled_at'
    ) THEN
        ALTER TABLE contents
            ADD INDEX idx_status_scheduled_at (status, scheduled_at);
    END IF;
END//

DELIMITER ;

CALL add_scheduled_at_if_missing();
CALL add_schedule_index_if_missing();

DROP PROCEDURE IF EXISTS add_scheduled_at_if_missing;
DROP PROCEDURE IF EXISTS add_schedule_index_if_missing;
