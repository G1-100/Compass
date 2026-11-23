# Instructions for an AI coding assistant: set up Compass env files

You are setting up local environment variables for a teammate on the Compass CSCI 201 project.
Work from the repository root. Follow these steps in order and report what you did at the end.

## Rules
- Never commit, push, paste into chat, or print `env-setup/secrets.env`, `backend/.env.local` or `frontend/.env.local`.
  When you show values to the user, mask the passwords (`***`).
- Only create or overwrite `backend/.env.local` and `frontend/.env.local`. Don't edit any other file.
- If `backend/.env.local` or `frontend/.env.local` already exists, show the user which keys would change
  (values masked) and ask before overwriting.

## Steps

1. **Check for the secrets file.** `env-setup/secrets.env` must exist and set non-empty `DB_PASSWORD` and
   `SMTP_PASSWORD`. If it's missing, stop and tell the user: "Ask Gavin for `secrets.env` (sent privately), save it
   as `env-setup/secrets.env`, then run me again." `env-setup/secrets.env.example` shows the format.

2. **Check git ignores everything secret.** Run
   `git check-ignore env-setup/secrets.env backend/.env.local frontend/.env.local`.
   All three paths must print. If any doesn't, stop and tell the user.

3. **Write `backend/.env.local`.** Copy `env-setup/backend.env` to `backend/.env.local`, then replace:
   - `__DB_PASSWORD__` → `DB_PASSWORD` from `secrets.env`, URL-encoded (for example `@` → `%40`, `#` → `%23`,
     `%` → `%25`, `&` → `%26`, `/` → `%2F`, `?` → `%3F`, `:` → `%3A`, space → `%20`). It goes inside a JDBC URL.
   - `__SMTP_PASSWORD__` → `SMTP_PASSWORD` from `secrets.env`, as is.
   Afterward, `grep __ backend/.env.local` must print nothing.

4. **Write `frontend/.env.local`.** Copy `env-setup/frontend.env` to `frontend/.env.local`. Ask the user whether
   they want `VITE_DEV_USER_ID=2` (act as the seeded team lead without logging in). Leave it blank if they don't.

5. **Test the database login (if `psql` is installed).** It may be at `/opt/homebrew/opt/libpq/bin/psql`.
   ```bash
   PGPASSWORD='<DB_PASSWORD>' PGCONNECT_TIMEOUT=10 psql "host=aws-0-us-east-1.pooler.supabase.com port=5432 dbname=postgres user=postgres.xurfounmawoasophhxpg sslmode=require" -tAc "select 'login ok'"
   ```
   `login ok` means the password works. `password authentication failed` means the password in `secrets.env` is
   wrong or was changed; tell the user to ask Gavin. If `psql` isn't installed, skip this and say so.

6. **Tell the user how to use the files.**
   - **Docker:** `docker build -t compass-api . && docker run --env-file backend/.env.local -p 8080:8080 compass-api`
   - **Eclipse/Tomcat:** Tomcat does not read `.env.local`. Paste each `KEY=value` line from `backend/.env.local`
     into Servers → Tomcat → *Open launch configuration* → *Environment*, then restart Tomcat.
   - **Frontend:** `cd frontend && npm install && npm run dev`. Restart `npm run dev` after any env change.
   - **Check:** `http://localhost:8080/api/health` should return `"database":"up"`.

7. **Report.** List the files you wrote, the masked values, and the result of the login test.
