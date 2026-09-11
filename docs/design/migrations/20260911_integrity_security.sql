-- UmoWeb compatibility migration for existing MySQL databases.
-- Back up the database before running this script.

USE umo_blog;

DELIMITER //

DROP PROCEDURE IF EXISTS add_column_if_missing//
CREATE PROCEDURE add_column_if_missing(
    IN table_name_value VARCHAR(64),
    IN column_name_value VARCHAR(64),
    IN column_definition_value TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = table_name_value
          AND column_name = column_name_value
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', table_name_value, '` ADD COLUMN ',
                          column_definition_value);
        PREPARE statement_to_run FROM @ddl;
        EXECUTE statement_to_run;
        DEALLOCATE PREPARE statement_to_run;
    END IF;
END//

DROP PROCEDURE IF EXISTS add_index_if_missing//
CREATE PROCEDURE add_index_if_missing(
    IN table_name_value VARCHAR(64),
    IN index_name_value VARCHAR(64),
    IN index_definition_value TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = table_name_value
          AND index_name = index_name_value
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', table_name_value, '` ADD INDEX `',
                          index_name_value, '` ', index_definition_value);
        PREPARE statement_to_run FROM @ddl;
        EXECUTE statement_to_run;
        DEALLOCATE PREPARE statement_to_run;
    END IF;
END//

DROP PROCEDURE IF EXISTS add_fk_if_missing//
CREATE PROCEDURE add_fk_if_missing(
    IN table_name_value VARCHAR(64),
    IN constraint_name_value VARCHAR(64),
    IN constraint_definition_value TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = table_name_value
          AND constraint_name = constraint_name_value
          AND constraint_type = 'FOREIGN KEY'
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', table_name_value, '` ADD CONSTRAINT `',
                          constraint_name_value, '` ', constraint_definition_value);
        PREPARE statement_to_run FROM @ddl;
        EXECUTE statement_to_run;
        DEALLOCATE PREPARE statement_to_run;
    END IF;
END//

DELIMITER ;

CALL add_column_if_missing(
    'users',
    'token_version',
    'token_version INT NOT NULL DEFAULT 1 AFTER password_hash');

CALL add_index_if_missing(
    'contents',
    'idx_published_at',
    '(published_at)');
CALL add_index_if_missing(
    'content_category',
    'idx_content_category_category_id',
    '(category_id)');
CALL add_index_if_missing(
    'content_tag',
    'idx_content_tag_tag_id',
    '(tag_id)');

-- Remove orphan rows before adding foreign keys.
DELETE cc
FROM content_category cc
LEFT JOIN contents c ON c.id = cc.content_id
LEFT JOIN categories category ON category.id = cc.category_id
WHERE c.id IS NULL OR category.id IS NULL;

DELETE ct
FROM content_tag ct
LEFT JOIN contents c ON c.id = ct.content_id
LEFT JOIN tags tag ON tag.id = ct.tag_id
WHERE c.id IS NULL OR tag.id IS NULL;

UPDATE categories child
LEFT JOIN categories parent ON parent.id = child.parent_id
SET child.parent_id = NULL
WHERE child.parent_id IS NOT NULL AND parent.id IS NULL;

CALL add_fk_if_missing(
    'categories',
    'fk_categories_parent',
    'FOREIGN KEY (parent_id) REFERENCES categories(id) ON DELETE RESTRICT');
CALL add_fk_if_missing(
    'content_category',
    'fk_content_category_content',
    'FOREIGN KEY (content_id) REFERENCES contents(id) ON DELETE CASCADE');
CALL add_fk_if_missing(
    'content_category',
    'fk_content_category_category',
    'FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT');
CALL add_fk_if_missing(
    'content_tag',
    'fk_content_tag_content',
    'FOREIGN KEY (content_id) REFERENCES contents(id) ON DELETE CASCADE');
CALL add_fk_if_missing(
    'content_tag',
    'fk_content_tag_tag',
    'FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE RESTRICT');

DROP PROCEDURE IF EXISTS add_column_if_missing;
DROP PROCEDURE IF EXISTS add_index_if_missing;
DROP PROCEDURE IF EXISTS add_fk_if_missing;
