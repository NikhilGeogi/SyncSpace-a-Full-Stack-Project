CREATE TABLE `users` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `username` VARCHAR(191) NOT NULL,
    `email` VARCHAR(191) NOT NULL,
    `password` VARCHAR(255) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_users_username` (`username`),
    UNIQUE KEY `uk_users_email` (`email`)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE `pages` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `title` VARCHAR(255) NOT NULL,
    `user_id` BIGINT NOT NULL,
    `parent_page_id` BIGINT DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_pages_user_id` (`user_id`),
    KEY `idx_pages_parent_page_id` (`parent_page_id`),
    CONSTRAINT `fk_pages_user`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_pages_parent`
        FOREIGN KEY (`parent_page_id`) REFERENCES `pages` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE `blocks` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `page_id` BIGINT NOT NULL,
    `block_type` VARCHAR(255) DEFAULT NULL,
    `content` TEXT,
    `sort_order` INT NOT NULL DEFAULT 0,
    `is_checked` BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (`id`),
    KEY `idx_blocks_page_id` (`page_id`),
    CONSTRAINT `fk_blocks_page`
        FOREIGN KEY (`page_id`) REFERENCES `pages` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
