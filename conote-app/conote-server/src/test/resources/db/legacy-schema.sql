-- Minimal pre-Stage-2 schema fixture. Use only in conote_stage2_legacy_test.
CREATE TABLE users (
    user_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_name VARCHAR(80), email VARCHAR(254) NOT NULL, password_hash VARCHAR(60) NOT NULL,
    full_name VARCHAR(120), avatar VARCHAR(512), is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE, created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notes (
    note_id VARCHAR(36) NOT NULL PRIMARY KEY, owner_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL, content TEXT, color VARCHAR(20) NOT NULL,
    is_pinned BOOLEAN NOT NULL DEFAULT FALSE, is_delete BOOLEAN NOT NULL DEFAULT FALSE,
    share_status VARCHAR(16) NOT NULL DEFAULT 'PRIVATE',
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tags (
    tag_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL,
    tag_name VARCHAR(50) NOT NULL, created_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE shares (
    share_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, note_id VARCHAR(36) NOT NULL,
    share_by BIGINT NOT NULL, share_with BIGINT NOT NULL, permission VARCHAR(8) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE note_orders (
    user_id BIGINT NOT NULL, note_id VARCHAR(36) NOT NULL,
    sort_order BIGINT NOT NULL DEFAULT 0, updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id, note_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO users (user_id,user_name,email,password_hash,full_name,is_verified,is_active,created_at,updated_at)
VALUES (1,'owner','owner@example.test','placeholder','Owner',FALSE,TRUE,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));
INSERT INTO notes (note_id,owner_id,title,content,color,is_pinned,is_delete,share_status,created_at,updated_at)
VALUES ('ffdf70d6-c37d-4339-8ef0-850dd49b2e63',1,'Old title','Old body','amber',TRUE,FALSE,'PRIVATE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));
INSERT INTO note_orders (user_id,note_id,sort_order,updated_at)
VALUES (1,'ffdf70d6-c37d-4339-8ef0-850dd49b2e63',5,UTC_TIMESTAMP(6));
