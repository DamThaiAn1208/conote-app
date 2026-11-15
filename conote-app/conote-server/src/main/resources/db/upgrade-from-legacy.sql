-- Back up an older CoNote database before running this migration, then run schema.sql.
-- Rerunnable: IF NOT EXISTS and upsert preserve rows. Unique indexes intentionally
-- fail if duplicate legacy rows need manual resolution.
ALTER TABLE notes
    ADD COLUMN IF NOT EXISTS note_type VARCHAR(16) NOT NULL DEFAULT 'TEXT',
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS search_text LONGTEXT;

UPDATE notes SET content = '' WHERE content IS NULL;
ALTER TABLE notes MODIFY COLUMN content LONGTEXT NOT NULL;
UPDATE notes SET note_type = 'TEXT' WHERE note_type IS NULL OR note_type = '';
UPDATE notes SET version = 1 WHERE version IS NULL OR version < 1;
-- Encoded rich text/checklist needs application decoding; plain content remains searchable.
UPDATE notes SET search_text = content
WHERE search_text IS NULL
  AND LEFT(content, CHAR_LENGTH('__CONOTE_RICH_TEXT__::')) <> '__CONOTE_RICH_TEXT__::'
  AND LEFT(content, CHAR_LENGTH('__CONOTE_CHECKLIST__::')) <> '__CONOTE_CHECKLIST__::';

ALTER TABLE note_orders
    ADD COLUMN IF NOT EXISTS is_pinned BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS is_favorite BOOLEAN NOT NULL DEFAULT FALSE;

-- Carry each owner's old note pin into the per-user row without replacing old order.
INSERT INTO note_orders (user_id, note_id, sort_order, is_pinned, is_favorite, updated_at)
SELECT n.owner_id, n.note_id, 0, COALESCE(n.is_pinned, FALSE), FALSE,
       COALESCE(n.updated_at, UTC_TIMESTAMP(6))
FROM notes n
WHERE n.is_delete = FALSE
ON DUPLICATE KEY UPDATE is_pinned = IF(note_orders.is_pinned, TRUE, VALUES(is_pinned));

ALTER TABLE shares ADD COLUMN IF NOT EXISTS created_at DATETIME(6);
UPDATE shares SET created_at = UTC_TIMESTAMP(6) WHERE created_at IS NULL;
ALTER TABLE shares MODIFY COLUMN created_at DATETIME(6) NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email ON users (email);
CREATE UNIQUE INDEX IF NOT EXISTS uq_tags_user_name ON tags (user_id, tag_name);
CREATE UNIQUE INDEX IF NOT EXISTS uq_shares_note_recipient ON shares (note_id, share_with);
CREATE INDEX IF NOT EXISTS idx_notes_owner_deleted ON notes (owner_id, is_delete);
CREATE INDEX IF NOT EXISTS idx_notes_updated ON notes (updated_at);
CREATE INDEX IF NOT EXISTS idx_shares_recipient ON shares (share_with);
CREATE INDEX IF NOT EXISTS idx_note_orders_user_sort ON note_orders (user_id, is_pinned, sort_order);
