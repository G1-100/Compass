-- =====================================================================
-- Compass database schema (PostgreSQL / Supabase)
-- Owner: Gavin (everyone may propose changes via PR)
--
-- Run in the Supabase SQL Editor, then run rls.sql, then (optionally) seed.sql.
-- Conventions:
--   * BIGINT identity primary keys, TIMESTAMPTZ in UTC, DATE for date-only.
--   * Every editable row has a `version` column for optimistic locking (409 VERSION_CONFLICT).
--   * Enums are TEXT + CHECK so they match the Java enum names exactly.
--   * Tables marked [ADDED] were not in the Detailed Design and were added so every feature has storage.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Accounts and authentication (Gavin)
-- ---------------------------------------------------------------------
CREATE TABLE users (
    user_id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email          VARCHAR(254) NOT NULL UNIQUE,
    password_hash  TEXT,                                   -- NULL for Google-only accounts
    google_sub     TEXT UNIQUE,                            -- [ADDED] Google account id for Google Sign-In
    display_name   VARCHAR(60)  NOT NULL,
    system_role    TEXT NOT NULL DEFAULT 'USER' CHECK (system_role IN ('USER', 'SYSTEM_ADMIN')),
    activated      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    version        INT NOT NULL DEFAULT 1
);

CREATE TABLE sessions (
    session_id_hash   TEXT PRIMARY KEY,
    user_id           BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_activity_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    invalidated       BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_sessions_user ON sessions(user_id);

CREATE TABLE verification_codes (
    code_id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    code_hash      TEXT NOT NULL,
    purpose        TEXT NOT NULL CHECK (purpose IN ('ACTIVATION', 'PASSWORD_RESET')),
    expires_at     TIMESTAMPTZ NOT NULL,
    attempts_used  INT NOT NULL DEFAULT 0,
    consumed_at    TIMESTAMPTZ
);
-- One active (unconsumed) code per user and purpose
CREATE UNIQUE INDEX uq_verification_active ON verification_codes(user_id, purpose) WHERE consumed_at IS NULL;

CREATE TABLE login_lockouts (
    email         VARCHAR(254) NOT NULL,
    action        TEXT NOT NULL CHECK (action IN ('LOGIN', 'PASSWORD_RESET', 'RESEND_CODE')),
    failed_count  INT NOT NULL DEFAULT 0,
    window_start  TIMESTAMPTZ NOT NULL DEFAULT now(),
    locked_until  TIMESTAMPTZ,
    PRIMARY KEY (email, action)
);

-- ---------------------------------------------------------------------
-- Teams and membership (Solomon)
-- ---------------------------------------------------------------------
CREATE TABLE teams (
    team_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(60) NOT NULL,
    created_by  BIGINT NOT NULL REFERENCES users(user_id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version     INT NOT NULL DEFAULT 1
);

CREATE TABLE team_memberships (
    membership_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    team_id        BIGINT NOT NULL REFERENCES teams(team_id) ON DELETE CASCADE,
    user_id        BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    role           TEXT NOT NULL CHECK (role IN ('MEMBER', 'LEAD')),
    joined_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version        INT NOT NULL DEFAULT 1,
    UNIQUE (team_id, user_id)
);
CREATE INDEX idx_memberships_user ON team_memberships(user_id);

CREATE TABLE team_invitations (
    invitation_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    team_id        BIGINT NOT NULL REFERENCES teams(team_id) ON DELETE CASCADE,
    invitee_email  VARCHAR(254) NOT NULL,
    role           TEXT NOT NULL CHECK (role IN ('MEMBER', 'LEAD')),
    status         TEXT NOT NULL DEFAULT 'PENDING'
                   CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'REVOKED', 'EXPIRED')),
    invited_by     BIGINT NOT NULL REFERENCES users(user_id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at     TIMESTAMPTZ NOT NULL,
    responded_at   TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_invitation_pending ON team_invitations(team_id, lower(invitee_email)) WHERE status = 'PENDING';
CREATE INDEX idx_invitations_email ON team_invitations(lower(invitee_email));

-- [ADDED] Audit trail for sensitive team actions (role changes, removals, guest links)
CREATE TABLE audit_log (
    audit_id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    team_id         BIGINT NOT NULL REFERENCES teams(team_id) ON DELETE CASCADE,
    actor_user_id   BIGINT REFERENCES users(user_id) ON DELETE SET NULL,
    target_user_id  BIGINT REFERENCES users(user_id) ON DELETE SET NULL,
    event_type      TEXT NOT NULL,   -- ROLE_CHANGED, MEMBER_REMOVED, MEMBER_LEFT, GUEST_LINK_CREATED, GUEST_LINK_REVOKED
    details         TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_team ON audit_log(team_id, created_at DESC);

-- ---------------------------------------------------------------------
-- Projects (Solomon) and guest links (Ramanjot)        [ADDED]
-- ---------------------------------------------------------------------
CREATE TABLE projects (
    project_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    team_id      BIGINT NOT NULL REFERENCES teams(team_id) ON DELETE CASCADE,
    name         VARCHAR(100) NOT NULL,
    description  VARCHAR(2000),
    target_date  DATE,
    status       TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('PLANNING', 'ACTIVE', 'ON_HOLD', 'COMPLETED')),
    archived_at  TIMESTAMPTZ,
    created_by   BIGINT NOT NULL REFERENCES users(user_id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    version      INT NOT NULL DEFAULT 1
);
CREATE INDEX idx_projects_team ON projects(team_id);

CREATE TABLE guest_links (
    link_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id  BIGINT NOT NULL REFERENCES projects(project_id) ON DELETE CASCADE,
    token_hash  TEXT NOT NULL UNIQUE,     -- raw token only ever appears in the share URL
    created_by  BIGINT NOT NULL REFERENCES users(user_id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ,              -- NULL = no expiry
    revoked_at  TIMESTAMPTZ
);
CREATE INDEX idx_guest_links_project ON guest_links(project_id);

-- ---------------------------------------------------------------------
-- Sprints (Siya) and tasks (Adam)        [ADDED]
-- ---------------------------------------------------------------------
CREATE TABLE sprints (
    sprint_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id    BIGINT NOT NULL REFERENCES projects(project_id) ON DELETE CASCADE,
    name          VARCHAR(100) NOT NULL,
    start_date    DATE NOT NULL,
    end_date      DATE NOT NULL,
    status        TEXT NOT NULL DEFAULT 'PLANNED' CHECK (status IN ('PLANNED', 'ACTIVE', 'COMPLETED')),
    completed_at  TIMESTAMPTZ,
    version       INT NOT NULL DEFAULT 1,
    CHECK (end_date > start_date)
);
CREATE INDEX idx_sprints_project ON sprints(project_id);

CREATE TABLE tasks (
    task_id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id         BIGINT NOT NULL REFERENCES projects(project_id) ON DELETE CASCADE,
    sprint_id          BIGINT REFERENCES sprints(sprint_id) ON DELETE SET NULL,   -- NULL = backlog
    title              VARCHAR(200) NOT NULL,
    description        VARCHAR(5000),
    due_date           DATE,
    effort             TEXT NOT NULL CHECK (effort IN ('MINOR', 'MODERATE', 'HIGH', 'EXTREME')),
    status             TEXT NOT NULL DEFAULT 'NOT_STARTED'
                       CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'BLOCKED', 'DONE')),
    -- Suggestions live in the same table. NULL = normal task.
    suggestion_status  TEXT CHECK (suggestion_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    suggested_by       BIGINT REFERENCES users(user_id) ON DELETE SET NULL,
    created_by         BIGINT NOT NULL REFERENCES users(user_id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    version            INT NOT NULL DEFAULT 1
);
CREATE INDEX idx_tasks_project ON tasks(project_id);
CREATE INDEX idx_tasks_sprint ON tasks(sprint_id);
CREATE INDEX idx_tasks_due ON tasks(project_id, due_date);

CREATE TABLE task_assignees (
    task_id             BIGINT NOT NULL REFERENCES tasks(task_id) ON DELETE CASCADE,
    user_id             BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    allocation_percent  NUMERIC(5,2),     -- NULL = split equally; when set, all rows for the task total 100
    assigned_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (task_id, user_id)
);
CREATE INDEX idx_task_assignees_user ON task_assignees(user_id);

CREATE TABLE task_comments (
    comment_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id       BIGINT NOT NULL REFERENCES tasks(task_id) ON DELETE CASCADE,
    author_id     BIGINT REFERENCES users(user_id) ON DELETE SET NULL,
    comment_text  VARCHAR(2000) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_task_comments_task ON task_comments(task_id, created_at);
CREATE INDEX idx_task_comments_author ON task_comments(author_id, created_at DESC);

CREATE TABLE task_activity (
    activity_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id        BIGINT REFERENCES tasks(task_id) ON DELETE CASCADE,
    sprint_id      BIGINT REFERENCES sprints(sprint_id) ON DELETE CASCADE,
    actor_user_id  BIGINT REFERENCES users(user_id) ON DELETE SET NULL,
    activity_type  TEXT NOT NULL,   -- TASK_CREATED, STATUS_CHANGED, TASK_MOVED_TO_NEXT_SPRINT, SPRINT_COMPLETED, ...
    old_value      TEXT,
    new_value      TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_task_activity_task ON task_activity(task_id, created_at);
CREATE INDEX idx_task_activity_actor ON task_activity(actor_user_id, created_at DESC);

-- ---------------------------------------------------------------------
-- Milestones (Siya)        [ADDED]
-- ---------------------------------------------------------------------
CREATE TABLE milestones (
    milestone_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id    BIGINT NOT NULL REFERENCES projects(project_id) ON DELETE CASCADE,
    name          VARCHAR(100) NOT NULL,
    description   VARCHAR(2000),
    target_date   DATE NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    version       INT NOT NULL DEFAULT 1
);
CREATE INDEX idx_milestones_project ON milestones(project_id);

CREATE TABLE milestone_tasks (
    milestone_id  BIGINT NOT NULL REFERENCES milestones(milestone_id) ON DELETE CASCADE,
    task_id       BIGINT NOT NULL REFERENCES tasks(task_id) ON DELETE CASCADE,
    PRIMARY KEY (milestone_id, task_id)
);
CREATE INDEX idx_milestone_tasks_task ON milestone_tasks(task_id);

-- ---------------------------------------------------------------------
-- Check-ins (Graham)
-- ---------------------------------------------------------------------
CREATE TABLE checkins (
    checkin_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    team_id       BIGINT NOT NULL REFERENCES teams(team_id) ON DELETE CASCADE,
    description   VARCHAR(2000),
    scheduled_at  TIMESTAMPTZ NOT NULL,
    location      VARCHAR(300),
    status        TEXT NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED', 'COMPLETED', 'CANCELED')),
    created_by    BIGINT NOT NULL REFERENCES users(user_id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at  TIMESTAMPTZ,
    version       INT NOT NULL DEFAULT 1
);
CREATE INDEX idx_checkins_team ON checkins(team_id, scheduled_at);

CREATE TABLE checkin_updates (
    update_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    checkin_id      BIGINT NOT NULL REFERENCES checkins(checkin_id) ON DELETE CASCADE,
    user_id         BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    completed_work  VARCHAR(2000),
    upcoming_tasks  VARCHAR(2000),
    submitted_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         INT NOT NULL DEFAULT 1,
    UNIQUE (checkin_id, user_id)
);
CREATE INDEX idx_checkin_updates_user ON checkin_updates(user_id, submitted_at DESC);

CREATE TABLE checkin_blockers (
    blocker_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    update_id    BIGINT NOT NULL REFERENCES checkin_updates(update_id) ON DELETE CASCADE,
    description  VARCHAR(500) NOT NULL,
    severity     TEXT NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH')),
    task_id      BIGINT REFERENCES tasks(task_id) ON DELETE SET NULL,
    resolved_at  TIMESTAMPTZ,
    resolved_by  BIGINT REFERENCES users(user_id) ON DELETE SET NULL
);
CREATE INDEX idx_blockers_task_unresolved ON checkin_blockers(task_id) WHERE resolved_at IS NULL;

CREATE TABLE checkin_reminders (
    checkin_id  BIGINT NOT NULL REFERENCES checkins(checkin_id) ON DELETE CASCADE,
    user_id     BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    sent_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (checkin_id, user_id)
);

-- ---------------------------------------------------------------------
-- Workload and risk (Liv)        [ADDED]
-- ---------------------------------------------------------------------
CREATE TABLE member_availability (
    project_id        BIGINT NOT NULL REFERENCES projects(project_id) ON DELETE CASCADE,
    user_id           BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    hours_per_week    NUMERIC(4,1) NOT NULL CHECK (hours_per_week BETWEEN 0 AND 80),
    reports_overload  BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (project_id, user_id)
);

CREATE TABLE workload_alerts (
    alert_id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id       BIGINT NOT NULL REFERENCES projects(project_id) ON DELETE CASCADE,
    user_id          BIGINT REFERENCES users(user_id) ON DELETE CASCADE,
    task_id          BIGINT REFERENCES tasks(task_id) ON DELETE CASCADE,
    alert_type       TEXT NOT NULL CHECK (alert_type IN ('OVERLOAD', 'AVAILABLE_CAPACITY', 'WORKLOAD_DRIFT', 'LATE_RISK')),
    severity         TEXT NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH')),
    reason           TEXT NOT NULL,
    rule_version     TEXT NOT NULL,
    status           TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED')),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    acknowledged_by  BIGINT REFERENCES users(user_id) ON DELETE SET NULL,
    acknowledged_at  TIMESTAMPTZ,
    resolved_at      TIMESTAMPTZ
);
-- At most one open/acknowledged alert of a type per member (or task) per project
CREATE UNIQUE INDEX uq_alert_open ON workload_alerts(project_id, alert_type, COALESCE(user_id, 0), COALESCE(task_id, 0))
    WHERE status <> 'RESOLVED';

-- ---------------------------------------------------------------------
-- Notifications and email (Raegen)
-- ---------------------------------------------------------------------
CREATE TABLE notifications (
    notif_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    notif_type     TEXT NOT NULL,
    project_id     BIGINT REFERENCES projects(project_id) ON DELETE CASCADE,
    task_id        BIGINT REFERENCES tasks(task_id) ON DELETE CASCADE,
    message        VARCHAR(1000) NOT NULL,
    is_read        BOOLEAN NOT NULL DEFAULT FALSE,
    read_at        TIMESTAMPTZ,
    email_sent_at  TIMESTAMPTZ,      -- design had NOT NULL; nullable because many notifications never email
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user ON notifications(user_id, created_at DESC);
CREATE INDEX idx_notifications_unread ON notifications(user_id) WHERE is_read = FALSE;

CREATE TABLE notification_preferences (
    notifp_id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          BIGINT NOT NULL UNIQUE REFERENCES users(user_id) ON DELETE CASCADE,
    email_enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    in_app_enabled   BOOLEAN NOT NULL DEFAULT TRUE,
    early_notif_time INTERVAL           -- NULL = default 24 hours before a check-in
);

-- [ADDED] Outbound email queue. Rows are inserted inside the caller's transaction;
-- the background EmailSenderJob delivers them and retries up to 3 times.
CREATE TABLE email_queue (
    email_id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    to_email         VARCHAR(254) NOT NULL,
    subject          VARCHAR(300) NOT NULL,
    body_text        TEXT NOT NULL,
    status           TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED')),
    attempts         INT NOT NULL DEFAULT 0,
    next_attempt_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error       TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at          TIMESTAMPTZ
);
CREATE INDEX idx_email_queue_pending ON email_queue(next_attempt_at) WHERE status = 'PENDING';
