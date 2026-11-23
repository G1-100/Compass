# Env setup for teammates

This folder lets your AI assistant (Claude Code, Cursor, Copilot, …) create your `backend/.env.local` and
`frontend/.env.local` with the team's values.

| File | In git? | What it is |
| --- | --- | --- |
| `backend.env` | yes | Team backend values, with `__DB_PASSWORD__` / `__SMTP_PASSWORD__` placeholders |
| `frontend.env` | yes | Team frontend values (all public) |
| `secrets.env.example` | yes | Format of the private file |
| `secrets.env` | **no, git-ignored** | The two real secrets: Supabase DB password and Resend API key |
| `AI_INSTRUCTIONS.md` | yes | Step-by-step instructions your assistant follows |

## What you do

1. `git pull` on `main`.
2. Get `secrets.env` from Gavin **privately** (password manager or DM, never the group chat) and save it as
   `env-setup/secrets.env`.
3. Tell your assistant:

   > Follow `env-setup/AI_INSTRUCTIONS.md` to set up my env files.

4. Start the backend and frontend as it tells you, then open `http://localhost:8080/api/health`.
   It should show `"database":"up"`.

## Notes

- Never commit `secrets.env` or any `.env.local`. They're already in `.gitignore`.
- If the DB password or Resend key changes, Gavin updates `secrets.env` and sends it again. Re-run the
  instructions.
- If a shared value changes (for example `EMAIL_FROM`), update `backend.env` / `frontend.env` here and commit, so
  everyone picks it up.
- Production values live in Render (backend) and Vercel (`VITE_GOOGLE_CLIENT_ID` only), not here.
  Never set `COMPASS_DEV_AUTH` on Render.
