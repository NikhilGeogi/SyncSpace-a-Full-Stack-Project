ALTER TABLE `pages`
    ADD COLUMN `public_token` VARCHAR(64) DEFAULT NULL,
    ADD UNIQUE KEY `uk_pages_public_token` (`public_token`);

ALTER TABLE `blocks`
    ADD COLUMN `due_date` DATE DEFAULT NULL,
    ADD COLUMN `todo_status` VARCHAR(16) NOT NULL DEFAULT 'todo',
    ADD COLUMN `overdue_flag` BOOLEAN NOT NULL DEFAULT FALSE,
    ADD KEY `idx_blocks_due_status` (`block_type`, `due_date`, `todo_status`);

UPDATE `blocks`
SET `todo_status` = IF(`is_checked`, 'done', 'todo')
WHERE `block_type` = 'todo';

CREATE TABLE `comments` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `block_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `content` TEXT NOT NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    KEY `idx_comments_block_created` (`block_id`, `created_at`),
    CONSTRAINT `fk_comments_block`
        FOREIGN KEY (`block_id`) REFERENCES `blocks` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_comments_user`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE `activity_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `page_id` BIGINT NOT NULL,
    `block_id` BIGINT DEFAULT NULL,
    `user_id` BIGINT NOT NULL,
    `action` VARCHAR(32) NOT NULL,
    `details` VARCHAR(500) NOT NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    KEY `idx_activity_page_created` (`page_id`, `created_at`),
    CONSTRAINT `fk_activity_page`
        FOREIGN KEY (`page_id`) REFERENCES `pages` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_activity_block`
        FOREIGN KEY (`block_id`) REFERENCES `blocks` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_activity_user`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
