ALTER TABLE task_status_history ADD COLUMN story_points INTEGER NOT NULL DEFAULT 0;

UPDATE task_status_history
SET story_points = (
    SELECT task.story_points
    FROM task
    WHERE task.id = task_status_history.task_id
)
WHERE EXISTS (
    SELECT 1
    FROM task
    WHERE task.id = task_status_history.task_id
);
