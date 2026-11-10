ALTER TABLE `pages`
    ADD COLUMN `deleted_at` DATETIME(6) DEFAULT NULL,
    ADD COLUMN `trash_root_id` BIGINT DEFAULT NULL,
    ADD KEY `idx_pages_trash_root_id` (`trash_root_id`),
    ADD FULLTEXT KEY `ft_pages_title` (`title`);

ALTER TABLE `blocks`
    MODIFY COLUMN `content` TEXT,
    ADD FULLTEXT KEY `ft_blocks_content` (`content`);

CREATE TABLE `page_shares` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `page_id` BIGINT NOT NULL,
    `shared_with_user_id` BIGINT NOT NULL,
    `permission` VARCHAR(16) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_page_shares_page_user` (`page_id`, `shared_with_user_id`),
    KEY `idx_page_shares_user` (`shared_with_user_id`),
    CONSTRAINT `fk_page_shares_page`
        FOREIGN KEY (`page_id`) REFERENCES `pages` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_page_shares_user`
        FOREIGN KEY (`shared_with_user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
