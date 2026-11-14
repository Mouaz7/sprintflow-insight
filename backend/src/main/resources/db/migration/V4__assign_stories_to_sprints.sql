ALTER TABLE user_stories ADD COLUMN sprint_id BIGINT;
CREATE INDEX story_sprint_idx ON user_stories(sprint_id);
