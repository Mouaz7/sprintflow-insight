ALTER TABLE user_stories ADD COLUMN backlog_position BIGINT NOT NULL DEFAULT 0;
UPDATE user_stories SET backlog_position = id;
CREATE INDEX idx_story_backlog_order ON user_stories(project_id, backlog_position, id);
