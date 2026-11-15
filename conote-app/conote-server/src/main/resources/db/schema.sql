-- Run against an existing MariaDB database: mariadb -u conote -p conote_db < schema.sql
-- Safe to rerun on a clean database. Upgrade an older CoNote database with
-- upgrade-from-legacy.sql first, then rerun this file for any missing tables.
CREATE TABLE IF NOT EXISTS users (
    user_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_name VARCHAR(80),
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(60) NOT NULL,
    full_name VARCHAR(120),
    avatar VARCHAR(512),
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS notes (
    note_id VARCHAR(36) NOT NULL PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content LONGTEXT NOT NULL,
    search_text LONGTEXT,
    color VARCHAR(20) NOT NULL DEFAULT 'default',
    is_pinned BOOLEAN NOT NULL DEFAULT FALSE, -- legacy owner value
    is_delete BOOLEAN NOT NULL DEFAULT FALSE,
    share_status VARCHAR(16) NOT NULL DEFAULT 'PRIVATE',
    note_type VARCHAR(16) NOT NULL DEFAULT 'TEXT',
    version BIGINT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_notes_owner FOREIGN KEY (owner_id) REFERENCES users(user_id),
    INDEX idx_notes_owner_deleted (owner_id, is_delete),
    INDEX idx_notes_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tags (
    tag_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    tag_name VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_tags_user FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT uq_tags_user_name UNIQUE (user_id, tag_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS note_tags (
    note_id VARCHAR(36) NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (note_id, tag_id),
    CONSTRAINT fk_note_tags_note FOREIGN KEY (note_id) REFERENCES notes(note_id) ON DELETE CASCADE,
    CONSTRAINT fk_note_tags_tag FOREIGN KEY (tag_id) REFERENCES tags(tag_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS shares (
    share_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    note_id VARCHAR(36) NOT NULL,
    share_by BIGINT NOT NULL,
    share_with BIGINT NOT NULL,
    permission VARCHAR(8) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_shares_note FOREIGN KEY (note_id) REFERENCES notes(note_id) ON DELETE CASCADE,
    CONSTRAINT fk_shares_sender FOREIGN KEY (share_by) REFERENCES users(user_id),
    CONSTRAINT fk_shares_recipient FOREIGN KEY (share_with) REFERENCES users(user_id),
    CONSTRAINT uq_shares_note_recipient UNIQUE (note_id, share_with),
    INDEX idx_shares_recipient (share_with)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS note_orders (
    user_id BIGINT NOT NULL,
    note_id VARCHAR(36) NOT NULL,
    sort_order BIGINT NOT NULL DEFAULT 0,
    is_pinned BOOLEAN NOT NULL DEFAULT FALSE,
    is_favorite BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id, note_id),
    CONSTRAINT fk_note_orders_user FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_note_orders_note FOREIGN KEY (note_id) REFERENCES notes(note_id) ON DELETE CASCADE,
    INDEX idx_note_orders_user_sort (user_id, is_pinned, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS email_otps (
    otp_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    email VARCHAR(254),
    otp_code VARCHAR(64),
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    purpose VARCHAR(20),
    expiry_time DATETIME(6),
    created_at DATETIME(6),
    CONSTRAINT fk_email_otps_user FOREIGN KEY (user_id) REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
