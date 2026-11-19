-- B8: verified project-member mentions and opt-in personal email summaries.
ALTER TABLE task_comment ADD CONSTRAINT task_comment_id_project_unique UNIQUE(id,project_id);
CREATE TABLE comment_mention (
 comment_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 PRIMARY KEY(comment_id,user_id),
 CONSTRAINT comment_mention_comment_project_fk FOREIGN KEY(comment_id,project_id)
  REFERENCES task_comment(id,project_id) ON DELETE CASCADE
);
CREATE INDEX comment_mention_user_idx ON comment_mention(user_id);
CREATE TABLE digest_subscription (
 user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
 frequency VARCHAR(8) NOT NULL CHECK(frequency IN ('OFF','DAILY','WEEKLY')),
 time_zone VARCHAR(64) NOT NULL,
 next_due_at TIMESTAMP WITH TIME ZONE,
 checkpoint_at TIMESTAMP WITH TIME ZONE,
 last_sent_at TIMESTAMP WITH TIME ZONE,
 token_version UUID NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 CHECK((frequency='OFF')=(next_due_at IS NULL))
);
CREATE INDEX digest_subscription_due_idx ON digest_subscription(next_due_at) WHERE next_due_at IS NOT NULL;
CREATE TABLE shedlock (
 name VARCHAR(64) NOT NULL PRIMARY KEY,
 lock_until TIMESTAMP NOT NULL,
 locked_at TIMESTAMP NOT NULL,
 locked_by VARCHAR(255) NOT NULL
);
