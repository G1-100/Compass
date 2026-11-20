# Compass

CSCI 201 team project — a project-management app for student teams: teams and roles, tasks and sprints,
milestones, check-ins, workload and risk alerts, guest sharing links, and notifications.

| Part | Tech | Hosted on |
| --- | --- | --- |
| `frontend/` | React 19 + TypeScript + Vite, Vitest for tests | Vercel |
| `backend/` | Java 21 servlets (Jakarta Servlet 6) on Tomcat 11, built with Maven | Docker host (see Deployment doc) |
| `database/` | PostgreSQL on Supabase, accessed over JDBC | Supabase project `xurfounmawoasophhxpg` |

The browser only talks to `/api/...` on its own origin. Vite (dev) and Vercel (prod) forward those requests to
Tomcat, so there is no CORS. The frontend never talks to Supabase directly.

**Start here:** [`docs/OWNERSHIP.md`](docs/OWNERSHIP.md) — who owns what and every deliverable.
Then read [`docs/CONTRIBUTING.md`](docs/CONTRIBUTING.md) for how the stubs and shared contracts work.

## Repository layout

```
backend/src/main/java/edu/usc/compass/
  AppContext.java            builds every service and route at startup (wiring)
  common/                    PLATFORM (done): router, errors, JSON, validation, DB/transactions, jobs, config
  auth/        Gavin         accounts, sessions, lockout, Google sign-in, PermissionService
  teams/       Solomon       teams, memberships, last-lead protection, invitations, audit log
  projects/    Solomon       projects, dashboard
  tasks/       Adam          tasks, assignees, suggestions, comments, @mentions, activity
  sprints/     Siya          sprints, CompleteSprint
  milestones/  Siya          milestones, progress calculation
  checkins/    Graham        check-ins, updates, blockers, reminder job
  workload/    Liv           availability, workload calculation, drift + late risk, alerts
  guest/       Ramanjot      guest links + read-only guest view
  notifications/ Raegen      in-app notifications, preferences, email queue + sender job
backend/src/test/...         platform tests + one *TestPlan per feature (disabled placeholders)
frontend/src/
  api/client.ts              PLATFORM (done): fetch wrapper, 15 s timeout, ApiError
  api/types.ts               TypeScript mirrors of every backend JSON shape
  api/<feature>.ts           one typed function per endpoint — call these, never fetch() directly
  auth/                      AuthContext + RequireAuth
  components/                Layout, TodoPage, FieldError, ErrorBanner, SubmitButton, ConflictDialog
  features/<feature>/        page stubs per owner
database/schema.sql          every table (Detailed Design + the missing ones)
database/rls.sql, seed.sql   lock out the anon key; dev seed data
```

## Local setup

Prerequisites (versions from the Deployment doc): Temurin JDK 21, Maven 3.9, Node.js 24 + npm, Tomcat 11.
Eclipse users: *File → Import → Existing Maven Projects* on `backend/`.

1. **Database** — in the Supabase dashboard, open *SQL Editor* and run `database/schema.sql`, then `database/rls.sql`,
   then (dev only) `database/seed.sql`. Get the JDBC string from *Connect → Session pooler → JDBC*, fill in the
   password and append `&sslmode=require`. The password lives in the team password manager, never in git.
2. **Backend** — set these environment variables on the Tomcat launch configuration (Eclipse: server → *Open launch
   configuration* → *Environment*):

   | Variable | Example | Notes |
   | --- | --- | --- |
   | `SUPABASE_DB_URL` | `jdbc:postgresql://aws-...pooler.supabase.com:5432/postgres?user=postgres.xurfounmawoasophhxpg&password=...&sslmode=require` | required |
   | `COMPASS_DEV_AUTH` | `true` | **local only** — lets you send `X-Dev-User-Id` instead of logging in |
   | `GOOGLE_CLIENT_ID` | `...apps.googleusercontent.com` | Google Sign-In |
   | `SMTP_HOST` / `SMTP_PORT` | `localhost` / `1025` | Mailpit locally |
   | `PUBLIC_SITE_URL` | `http://localhost:5173` | used in email + guest links |

   Deploy to Tomcat with context root `/` (or set `VITE_BACKEND_URL` below to include your context path), start it,
   and check `http://localhost:8080/api/health` returns `{"status":"ok","database":"up",...}`.
   Or skip Eclipse: `docker build -t compass-api . && docker run -p 8080:8080 -e SUPABASE_DB_URL=... compass-api`.
3. **Frontend**
   ```bash
   cd frontend
   cp .env.example .env.local      # set VITE_DEV_USER_ID=2 to act as the seeded team lead
   npm install
   npm run dev                     # http://localhost:5173
   ```

Every endpoint already exists. Ones whose service is still a stub return **501 NOT_IMPLEMENTED** naming the owner.

## Environment variables

Templates live in git; real values never do. `.env`, `.env.local` and `.env.*.local` are already in `.gitignore`.

| File (git-ignored copy) | Template | Read by | Where the same values go in production |
| --- | --- | --- | --- |
| `backend/.env.local` | `backend/.env.example` | Docker (`--env-file`); Eclipse users paste the lines into the Tomcat launch configuration's Environment tab. Tomcat does not read the file itself. | Render service → Environment (without `COMPASS_DEV_AUTH`) |
| `frontend/.env.local` | `frontend/.env.example` | Vite, at dev-server start and build time | Vercel → Settings → Environment Variables (`VITE_GOOGLE_CLIENT_ID` only), then redeploy |

- **Secrets** (`SUPABASE_DB_URL` with its password, `SMTP_PASSWORD`) are shared through the team password manager, not by
  sending each other `.env` files. Each person copies the template and fills in their own copy.
- **`VITE_*` values are public**: Vite builds them into the JavaScript bundle. Only the Google client ID belongs there.
- `GOOGLE_CLIENT_ID` (backend) and `VITE_GOOGLE_CLIENT_ID` (frontend) are the same value.
- Restart Tomcat, or the Vite dev server, after changing a value. Vercel needs a redeploy.
- The session cookie is `Secure`. Chrome and Firefox accept it on `http://localhost`, but Safari does not, so test
  local sign-in in Chrome or Firefox.
- Seeded accounts have no password. To sign in as one, use *Forgot password* on the sign-in page. While Raegen's
  email queue is still a stub, the 6-digit code is printed in the Tomcat console (`[EMAIL STUB]`).

## Commands

| | Backend (`backend/`) | Frontend (`frontend/`) |
| --- | --- | --- |
| Build | `mvn package` → `target/compass-api.war` | `npm run build` → `dist/` |
| Test | `mvn test` (JUnit 5 + Mockito) | `npm test` (Vitest) |

CI runs both on every push and pull request.

## Known gaps in the design docs

- The Detailed Design says MySQL; we use **PostgreSQL (Supabase)** per the Deployment doc.
- Tables for projects, tasks, sprints, milestones, guest links, availability, alerts, audit log and the email queue
  were missing from the design; they are in `schema.sql` marked `[ADDED]`.
- Google Sign-In is in the Deployment doc only; it is stubbed as `POST /api/auth/google`.
- Regression test 5 mentions a check-in "confidence" value that the design does not have — decide and add it, or drop it.
