ALTER TABLE sprint ADD CONSTRAINT sprint_identity_project UNIQUE(id,project_id);
ALTER TABLE user_stories ADD CONSTRAINT story_identity_project UNIQUE(id,project_id);
ALTER TABLE task ADD CONSTRAINT task_project_fk FOREIGN KEY(project_id) REFERENCES projects(id);
ALTER TABLE sprint ADD CONSTRAINT sprint_project_fk FOREIGN KEY(project_id) REFERENCES projects(id);
ALTER TABLE user_stories ADD CONSTRAINT story_project_fk FOREIGN KEY(project_id) REFERENCES projects(id);
ALTER TABLE task ADD CONSTRAINT task_sprint_project_fk FOREIGN KEY(sprint_id,project_id) REFERENCES sprint(id,project_id);
ALTER TABLE task ADD CONSTRAINT task_story_project_fk FOREIGN KEY(story_id,project_id) REFERENCES user_stories(id,project_id);
ALTER TABLE user_stories ADD CONSTRAINT story_sprint_project_fk FOREIGN KEY(sprint_id,project_id) REFERENCES sprint(id,project_id);
