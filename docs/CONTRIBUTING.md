# How we work in this repo

## Workflow

1. Pull `main`, branch as `yourname/short-description` (e.g. `adam/task-create`).
2. Work only inside your feature's packages/folders where you can (see `OWNERSHIP.md`).
3. Open a pull request; CI must pass; get one teammate's review; squash-merge.
4. Never commit secrets (`.env.local`, DB passwords, SMTP credentials).

## How the stubs work (why nobody is blocked)

Every class, method, route and API function already exists with its final signature and a comment saying what it
must do. You replace bodies; you should rarely need to change a signature.

- **Feature stubs** — `throw Todo.of("Adam", "TaskService.create");` The route returns **501 NOT_IMPLEMENTED**
  until you implement it. Find your work: search for `Todo.of("YourName"`.
- **Cross-feature hooks are safe no-ops** — methods other people call return normally while unimplemented, so your
  code can call them today:

  | Hook | Owner | Stub behavior |
  | --- | --- | --- |
  | `PermissionService.require / has / requireAssigneeOrLead / requireSystemAdmin` | Gavin | allows everything |
  | `AuthService.authenticate` | Gavin | returns empty (use dev auth meanwhile) |
  | `NotificationService.notify / createNotification` | Raegen | does nothing |
  | `EmailQueue.enqueue` | Raegen | logs the email to the Tomcat console |
  | `ActivityService.record` | Adam | does nothing |
  | `AuditLogService.record` | Solomon | does nothing |
  | `WorkloadService.onAssignmentsChanged` | Liv | does nothing |
  | `LateRiskDetector.detectLateRisk` | Liv | returns `UNKNOWN` |
  | `MilestoneService.listForProject` | Siya | returns an empty list |
  | `EmailTemplates.*` | Raegen | returns a placeholder message |

  Write your code as if these were real (call `permissions.require(...)` before every change, call `notify(...)`
  after every change). When the owner finishes, your feature starts enforcing/notifying with no edits.
- **Already implemented shared lookups:** `ProjectDao.findTeamId`, `ProjectService.requirePermission`,
  `TaskDao.findProjectId` — so permission checks work from day one.
- **DAOs you depend on but don't own** (e.g. Liv uses `TaskDao.findActiveAssignments`): mock them in unit tests
  with Mockito until the owner implements them. Keep marked `SHARED` signatures stable.

## Dev auth (work before login exists)

Set `COMPASS_DEV_AUTH=true` on your local Tomcat and `VITE_DEV_USER_ID=<user id>` in `frontend/.env.local`.
Every request then acts as that user with admin rights. With curl: `-H "X-Dev-User-Id: 2"`.
**Never set `COMPASS_DEV_AUTH` in production.**

## Backend conventions (Detailed Design 3.5 — already enforced by the platform)

- One controller per feature registers routes on the `Router`; services hold logic; DAOs hold SQL and take a
  `Connection` so services control transactions:
  ```java
  return db.inTransaction(c -> {
      long teamId = projects.requirePermission(c, ctx, projectId, Permission.MANAGE_TASKS);
      new Validator().required("title", req.title()).maxLength("title", req.title(), 200).throwIfInvalid();
      long id = taskDao.insert(c, ...);
      activity.record(c, id, null, ctx.userId(), ActivityType.TASK_CREATED, null, req.title());
      notifications.notify(c, new NotificationRequest(...));
      return taskDao.findById(c, id).orElseThrow();
  });
  ```
- Errors: always `throw ApiException.xxx(...)` — `validation`, `forbidden`, `notFound`, `versionConflict(current)`,
  `invalidState`, `lockedOut`. The dispatcher formats the JSON body. Never return error objects yourself.
- Optimistic locking: every editable row has `version`. `UPDATE ... WHERE id = ? AND version = ?`; zero rows
  updated → `throw ApiException.versionConflict(latestRecord)`.
- Concurrency: use `SELECT ... FOR UPDATE` or unique constraints (they are in `schema.sql`), not Java locks.
- Time: use the injected `Clock` (`Instant.now(clock)`), never `Instant.now()` directly — tests can fix the time.
- Thresholds live in `common/Rules.java`.

## Frontend conventions

- Call the typed functions in `src/api/*.ts`; never call `fetch` directly.
- Use `SubmitButton` (disables while saving), `FieldError` (server field messages), `ErrorBanner` (timeout + Retry),
  and `ConflictDialog` (409). Keep form state in the page so inputs survive errors.
- If you change a backend record, update `src/api/types.ts` in the same PR.
- Tests go next to the file: `Thing.test.tsx`.
