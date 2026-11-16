-- Development seed data. Safe to run on an empty dev database only.
-- Seeded accounts have no password yet (password_hash NULL). Until registration/login works, run the backend
-- with COMPASS_DEV_AUTH=true and send X-Dev-User-Id: <id> (1 = admin, 2 = team lead, 3/4 = members).
-- Once PasswordHasher works, register real accounts or UPDATE users SET password_hash = '<bcrypt>' for these.

INSERT INTO users (email, password_hash, display_name, system_role, activated) VALUES
  ('admin@usc.edu', NULL, 'Admin User',  'SYSTEM_ADMIN', TRUE),
  ('lead@usc.edu',  NULL, 'Lena Lead',   'USER', TRUE),
  ('alex@usc.edu',  NULL, 'Alex Smith',  'USER', TRUE),
  ('jordan@usc.edu',NULL, 'Jordan Lee',  'USER', TRUE);

INSERT INTO teams (name, created_by) VALUES ('CSCI 201 Team', 2);

INSERT INTO team_memberships (team_id, user_id, role) VALUES
  (1, 2, 'LEAD'), (1, 3, 'MEMBER'), (1, 4, 'MEMBER');

INSERT INTO projects (team_id, name, description, target_date, created_by)
VALUES (1, 'Compass Prototype', 'Seed project for local development', CURRENT_DATE + 30, 2);

INSERT INTO sprints (project_id, name, start_date, end_date, status) VALUES
  (1, 'Sprint 1', CURRENT_DATE - 7, CURRENT_DATE + 7, 'ACTIVE'),
  (1, 'Sprint 2', CURRENT_DATE + 7, CURRENT_DATE + 21, 'PLANNED');

INSERT INTO tasks (project_id, sprint_id, title, due_date, effort, status, created_by) VALUES
  (1, 1, 'Set up repository',        CURRENT_DATE - 2, 'MINOR',    'DONE',        2),
  (1, 1, 'Implement dashboard',      CURRENT_DATE + 5, 'HIGH',     'IN_PROGRESS', 2),
  (1, 1, 'Write security test plan', CURRENT_DATE + 3, 'MODERATE', 'NOT_STARTED', 2),
  (1, NULL, 'Create user tutorial',  CURRENT_DATE + 20,'MODERATE', 'NOT_STARTED', 2);

INSERT INTO task_assignees (task_id, user_id) VALUES (1, 3), (2, 3), (3, 4), (4, 4);

INSERT INTO milestones (project_id, name, target_date) VALUES (1, 'Complete MVP', CURRENT_DATE + 14);
INSERT INTO milestone_tasks (milestone_id, task_id) VALUES (1, 1), (1, 2), (1, 3);

INSERT INTO member_availability (project_id, user_id, hours_per_week) VALUES (1, 3, 10), (1, 4, 10);
