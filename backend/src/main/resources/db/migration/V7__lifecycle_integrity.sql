ALTER TABLE task ADD COLUMN deletion_batch_id VARCHAR(36);
ALTER TABLE sprint ADD COLUMN deletion_batch_id VARCHAR(36);
ALTER TABLE user_stories ADD COLUMN deletion_batch_id VARCHAR(36);
ALTER TABLE activity_event ADD COLUMN actor_user_id BIGINT;
ALTER TABLE activity_event ADD COLUMN entity_type VARCHAR(32);
ALTER TABLE activity_event ADD COLUMN entity_id BIGINT;
ALTER TABLE task ADD CONSTRAINT task_points_range CHECK (story_points BETWEEN 0 AND 1000);
ALTER TABLE sprint ADD CONSTRAINT sprint_points_nonnegative CHECK (total_story_points >= 0);
ALTER TABLE user_stories ADD CONSTRAINT story_points_allowed CHECK (story_points IN (1,2,3,5,8));
