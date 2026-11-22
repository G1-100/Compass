# Code ownership and deliverables

Mirrors the team's *Code Responsibilities* doc. Each feature owner builds their part end to end: SQL (DAO),
service logic, routes (already wired), React pages, and tests. Search for `Todo.of("YourName"` to see your remaining
backend work; your page stubs are in `frontend/src/features/<feature>/`. Testing Plan case numbers are in brackets.

## Auth and Accounts — Gavin
Backend `auth/` · Frontend `features/auth/`, `auth/AuthContext.tsx` · Tests `AuthTestPlan`
- [ ] `PasswordHasher` (bcrypt) and password policy
- [ ] `SessionManager`: create/validate/touch/invalidate sessions, HttpOnly + Secure + SameSite=Lax cookie, 30-min timeout [WB3]
- [ ] `LoginLockoutService`: 5 failures in 30 min → 30-min lock, 429 + Retry-After [WB1, WB2, BB3, Stress 4]
- [ ] `VerificationCodeService`: 6-digit hashed codes, 1-hour expiry, 5 attempts [WB4]
- [ ] `AuthService`: register, activate, resend code, login, logout, me, password reset, `authenticate` for AuthFilter
- [ ] `AuthFilter`: read session cookie → AuthContext
- [ ] `GoogleTokenVerifier` + `loginWithGoogle` (Google Cloud setup per Deployment doc)
- [ ] `PermissionService` real checks (system admin / team role / assignee) [WB5]
- [ ] `UserService`: profile, admin user list, change system role [Reg 1]
- [ ] DAOs: `UserDao`, `SessionDao`, `VerificationCodeDao`, `LoginLockoutDao`
- [ ] Pages: Sign in (+ Google button), Register, Activate, Reset password, Profile, Admin users; restore session in `AuthContext`

## Teams and Projects/Dashboard — Solomon
Backend `teams/`, `projects/` · Frontend `features/teams/` · Tests `TeamsTestPlan`
- [ ] `TeamService`: create (creator becomes lead), list, get, rename, delete, list members [Unit 7, 8]
- [ ] `MembershipService.updateTeamMembership`: change role / remove / leave with last-lead protection [WB6, BB10, Unit 1-6, Stress 9]
- [ ] `InvitationService`: invite, list, revoke, my invitations, accept, decline; `Invitation.isExpired` [Unit 9-12]
- [ ] `AuditLogService`: record + list
- [ ] `ProjectService`: search, list, create, get, update, archive [Reg 2]
- [ ] `DashboardService`: progress, team assignments, deadlines, milestones, my tasks [Reg 6, 7, 13, 14, Stress 2]
- [ ] DAOs: `TeamDao`, `MembershipDao` (incl. `findRolesForUser` for auth), `InvitationDao`, `AuditDao`, `ProjectDao`
- [ ] Pages: My teams, Team (projects), Team members (+ invites, audit modal), My invitations, Project dashboard

## Tasks — Adam
Backend `tasks/` · Frontend `features/tasks/` · Tests `TasksTestPlan`
- [ ] `TaskService`: list with filters/sort/paging, my tasks, create, get, update, status change (assignee only), replace assignees with % split, delete [BB4, BB8, WB7, Reg 8, 12, Stress 1, 18]
- [ ] `SuggestionService`: suggest, approve (exactly once), reject [BB5, Reg 9, 10]
- [ ] `CommentService` + `MentionParser`: comments with @mentions → one notification each [Stress 3]
- [ ] `ActivityService`: record + list (activity modal) [Reg 7, Stress 17]
- [ ] `TaskDao` incl. SHARED queries used by sprints, workload, dashboard; `CommentDao`; `ActivityDao`
- [ ] Pages: Task board, Task detail (edit, assignees, comments, activity), My tasks

## Sprints and Milestones — Siya
Backend `sprints/`, `milestones/` · Frontend `features/sprints/` · Tests `SprintsMilestonesTestPlan`
- [ ] `SprintService`: list, create, update, start, delete; `completeSprint` in one transaction with rollback [BB9, WB8, WB17, Reg 4, Stress 11, 12]
- [ ] `MilestoneProgressCalculator`: effort-weighted progress + deadline status [WB18, BB11, BB12, Stress 15]
- [ ] `MilestoneService`: list, create, get, update, replace linked tasks, delete, `listForProject` (for dashboard/guest) [Reg 3]
- [ ] DAOs: `SprintDao`, `MilestoneDao`
- [ ] Pages: Sprints (+ Complete Sprint dialog), Milestones

## Check-Ins — Graham
Backend `checkins/` · Frontend `features/checkins/` · Tests `CheckinsTestPlan`
- [ ] `CheckinService`: list, schedule (future time, location rules), get, update/reschedule, cancel, complete (locks updates) [Unit 13-16, Reg 11]
- [ ] Progress updates: create-or-replace with blockers in one transaction [Unit 17-19, Reg 5, Stress 16]
- [ ] Resolve blocker (author or lead) [Unit 20]; `Checkin.acceptsUpdates`, `Blocker.isResolved`
- [ ] `CheckinReminderJob`: one reminder per member per check-in, no duplicates [Stress 7]
- [ ] `CheckinDao` incl. SHARED blocker/last-update queries used by Liv
- [ ] Pages: Check-ins list, Check-in detail (my update form, all updates, lead actions)

## Workload and Risk — Liv
Backend `workload/` · Frontend `features/workload/` · Tests `WorkloadTestPlan`
- [ ] `WorkloadCalculator`: shares, overload/capacity flags, equal or % split, insufficient data [WB9, WB13, WB14, Stress 13]
- [ ] `WorkloadService`: report availability, workload view, `onAssignmentsChanged`
- [ ] `DriftDetector` [WB15, WB16] and `LateRiskDetector` (shown on every task) [Stress 14]
- [ ] `AlertService`: raise once / resolve / list / acknowledge
- [ ] `RiskDetectionJob`: daily sweep, safe with overlapping runs
- [ ] DAOs: `WorkloadDao`, `AlertDao`
- [ ] UI: Workload panel (dashboard), My availability, Alerts page

## Sharing / Guest Links — Ramanjot
Backend `guest/` · Frontend `features/guest/` · Tests `GuestTestPlan`
- [x] `GuestLinkService`: create (random token, store hash, share URL), list with status, revoke, audit events [BB6]
- [x] `viewByToken`: approved fields only; unknown/expired/revoked → 404 GUEST_LINK_UNAVAILABLE [BB7, Stress 10]
- [x] `GuestLinkDao`
- [x] UI: Guest links panel (dashboard; Solomon mounts `<GuestLinksPanel projectId>` for leads), public Guest view page
- [ ] Hosting: Vercel project and Render backend (see Platforms > Hosting and Backend)

## Notifications / Emailing — Raegen
Backend `notifications/` · Frontend `features/notifications/` · Tests `NotificationsTestPlan`
- [ ] `NotificationService.notify / createNotification` respecting preferences [Stress 5]
- [ ] List, unread count, mark read, preferences, custom lead notification [Stress 8]
- [ ] `EmailQueue.enqueue` (insert row) and `EmailTemplates`
- [ ] `EmailSenderJob`: claim with SKIP LOCKED, send, retry up to 3, mark failed [Stress 6]
- [ ] `SmtpMailer` (Mailpit locally, provider in production)
- [ ] Email provider setup, suggested Resend (any SMTP provider works; only the `SMTP_*` env vars change). Give the SMTP values to Ramanjot for Render
- [ ] DAOs: `NotificationDao`, `EmailQueueDao`
- [ ] UI: Notification bell with unread badge, Notifications page, Notification settings

## Platforms

### Database (Supabase) — Gavin, and everyone
- [x] `database/schema.sql` with every table, constraints and indexes; `rls.sql`; `seed.sql`
- [ ] Create the Supabase project tables (run the three scripts), share the JDBC string via the password manager
- [ ] Everyone: write your feature's SQL in your DAOs; propose schema changes by PR to `schema.sql`

### Hosting (Vercel) — Ramanjot
- [x] `frontend/vercel.json` (API rewrite, SPA fallback, no API caching)
- [ ] Create the Vercel project (root `frontend`, Node 24), set `VITE_GOOGLE_CLIENT_ID`, replace `[HOST-PUBLIC-URL]`

### Backend — deploy: Ramanjot
- [x] Platform: router + dispatcher, error format, JSON, validation, paging, DB pool + transactions, job scheduler, config, health check, dev auth, wiring (`AppContext`)
- [x] `Dockerfile` (Tomcat 11 + WAR as ROOT), CI workflow
- [ ] Deploy the container, suggested Render (Railway, Fly.io or Cloud Run work with the same Dockerfile): Web Service, Docker, port 8080, health check `/api/health`, env vars from `backend/.env.local` minus `COMPASS_DEV_AUTH`
- [ ] Keep it awake (paid instance, or an uptime monitor hitting `/api/health` every 10 min) so email and reminders keep running

### Frontend — Adam, and everyone
- [x] Vite + React + TS + Vitest, routing, API client (15 s timeout, ApiError), typed API modules, AuthContext, layout
- [ ] Adam: finish shared components (`ConflictDialog` UI), styling from Figma, layout/navigation polish
- [ ] Everyone: replace your `<TodoPage>` stubs with real pages
