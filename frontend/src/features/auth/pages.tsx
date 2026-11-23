// Auth and account pages. Owner: Gavin.
import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router';
import { authApi, usersApi } from '../../api/auth';
import { ApiError, setDevUserId } from '../../api/client';
import type { Page, SystemRole, User } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { ConflictDialog, ErrorBanner, FieldError, SubmitButton } from '../../components/shared';
import { GoogleSignInButton } from './GoogleSignInButton';
import styles from './AuthPages.module.css';
import './auth.css';

/** "Try again in 12 minutes." from a 429's Retry-After, or the server message as-is. */
export function lockoutMessage(err: ApiError): string {
  if (err.retryAfterSeconds && err.retryAfterSeconds > 0) {
    const minutes = Math.ceil(err.retryAfterSeconds / 60);
    return `${err.message.replace(/Try again in \d+ minutes?\./, '').trim()} Try again in ${minutes} minute${minutes === 1 ? '' : 's'}.`;
  }
  return err.message;
}

function isCode(err: unknown, code: ApiError['code']): err is ApiError {
  return err instanceof ApiError && err.code === code;
}

/** Errors shown as a banner (field errors are shown next to their inputs instead). */
function bannerError(err: unknown): unknown {
  if (err instanceof ApiError && err.code === 'VALIDATION_FAILED' && Object.keys(err.fields).length > 0) return null;
  return err;
}

// ---- Sign in ------------------------------------------------------------------------------------------

export function SignInPage() {
  const { setMe } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const from = (location.state as { from?: string } | null)?.from ?? '/teams';
  const notice = searchParams.get('notice');

  const [email, setEmail] = useState(searchParams.get('email') ?? '');
  const [password, setPassword] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<unknown>(null);

  async function submit(e?: FormEvent) {
    e?.preventDefault();
    setPending(true);
    setError(null);
    try {
      setMe(await authApi.login({ email, password }));
      navigate(from, { replace: true });
    } catch (err) {
      setError(err);
      setPassword('');
    } finally {
      setPending(false);
    }
  }

  async function google(credential: string) {
    setError(null);
    try {
      setMe(await authApi.loginWithGoogle(credential));
      navigate(from, { replace: true });
    } catch (err) {
      setError(err);
    }
  }

  return (
    <section className="auth-card">
      <h1>Sign in to Compass</h1>
      {notice === 'activated' && <p className="auth-notice">Your account is active. Sign in to continue.</p>}
      {notice === 'reset' && <p className="auth-notice">Your password was changed. Sign in with the new one.</p>}

      {isCode(error, 'LOCKED_OUT') ? (
        <div className="error-banner" role="alert">{lockoutMessage(error)}</div>
      ) : isCode(error, 'INVALID_STATE') ? (
        <div className="error-banner" role="alert">
          {error.message} <Link to={`/activate?email=${encodeURIComponent(email)}`}>Enter your code</Link>
        </div>
      ) : (
        <ErrorBanner error={bannerError(error)} onRetry={() => void submit()} />
      )}

      <form onSubmit={submit} className="auth-form" noValidate>
        <label>
          Email
          <input type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          <FieldError error={error} field="email" />
        </label>
        <label>
          Password
          <input type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          <FieldError error={error} field="password" />
        </label>
        <SubmitButton pending={pending}>Sign in</SubmitButton>
      </form>

      <GoogleSignInButton onCredential={(c) => void google(c)} />

      <p className="auth-links">
        <Link to="/register">Create an account</Link> · <Link to="/reset-password">Forgot password?</Link>
      </p>

      {import.meta.env.DEV && <DevSignIn from={from} />}
    </section>
  );
}

/**
 * LOCAL DEVELOPMENT ONLY (Adam): act as a seeded user via X-Dev-User-Id without a password.
 * Needs COMPASS_DEV_AUTH=true on the backend. Vite strips this from production builds.
 */
function DevSignIn({ from }: { from: string }) {
  const navigate = useNavigate();
  const [devUser, setDevUser] = useState('2');
  const [error, setError] = useState('');

  function signInAsDeveloper(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    try {
      setDevUserId(devUser);
      navigate(from, { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not start developer session');
    }
  }

  return (
    <form onSubmit={signInAsDeveloper} className={styles.devForm}>
      <label htmlFor="dev-user">Local development access</label>
      <select id="dev-user" value={devUser} onChange={(event) => setDevUser(event.target.value)}>
        <option value="2">Lena Lead (seeded team lead)</option>
        <option value="1">Admin User</option>
        <option value="3">Alex Smith</option>
        <option value="4">Jordan Lee</option>
      </select>
      <p className={styles.hint}>
        Requires <code>COMPASS_DEV_AUTH=true</code> on your local backend. This option is unavailable in production.
      </p>
      {error && <p className={styles.error} role="alert">{error}</p>}
      <button type="submit" className={styles.submitButton}>
        Continue as dev user
      </button>
    </form>
  );
}

// ---- Register -----------------------------------------------------------------------------------------

export function RegisterPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState({ email: '', displayName: '', password: '' });
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<unknown>(null);

  async function submit(e?: FormEvent) {
    e?.preventDefault();
    setPending(true);
    setError(null);
    try {
      await authApi.register(form);
      navigate(`/activate?email=${encodeURIComponent(form.email.trim())}&sent=1`);
    } catch (err) {
      setError(err);
    } finally {
      setPending(false);
    }
  }

  return (
    <section className="auth-card">
      <h1>Create account</h1>
      <ErrorBanner error={bannerError(error)} onRetry={() => void submit()} />
      <form onSubmit={submit} className="auth-form" noValidate>
        <label>
          Email
          <input type="email" autoComplete="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} required />
          <FieldError error={error} field="email" />
        </label>
        <label>
          Display name
          <input autoComplete="name" maxLength={60} value={form.displayName} onChange={(e) => setForm({ ...form, displayName: e.target.value })} required />
          <FieldError error={error} field="displayName" />
        </label>
        <label>
          Password
          <input type="password" autoComplete="new-password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} required />
          <span className="muted small">At least 8 characters, with a letter and a number.</span>
          <FieldError error={error} field="password" />
        </label>
        <SubmitButton pending={pending}>Create account</SubmitButton>
      </form>
      <p className="auth-links">
        Already have an account? <Link to="/sign-in">Sign in</Link>
      </p>
    </section>
  );
}

// ---- Activate -----------------------------------------------------------------------------------------

export function ActivatePage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [email, setEmail] = useState(searchParams.get('email') ?? '');
  const [code, setCode] = useState('');
  const [pending, setPending] = useState(false);
  const [resending, setResending] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [info, setInfo] = useState<string | null>(
    searchParams.get('sent') ? 'We emailed you a 6-digit code. It expires in 1 hour.' : null,
  );

  async function submit(e?: FormEvent) {
    e?.preventDefault();
    setPending(true);
    setError(null);
    try {
      await authApi.activate({ email, code });
      navigate(`/sign-in?notice=activated&email=${encodeURIComponent(email.trim())}`);
    } catch (err) {
      setError(err);
    } finally {
      setPending(false);
    }
  }

  async function resend() {
    setResending(true);
    setError(null);
    setInfo(null);
    try {
      await authApi.resendCode({ email });
      setInfo('If that account needs activating, a new code is on its way. Older codes no longer work.');
    } catch (err) {
      setError(err);
    } finally {
      setResending(false);
    }
  }

  return (
    <section className="auth-card">
      <h1>Activate account</h1>
      {info && <p className="auth-notice">{info}</p>}
      {isCode(error, 'LOCKED_OUT') ? (
        <div className="error-banner" role="alert">{lockoutMessage(error)}</div>
      ) : (
        <ErrorBanner error={bannerError(error)} onRetry={() => void submit()} />
      )}
      <form onSubmit={submit} className="auth-form" noValidate>
        <label>
          Email
          <input type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          <FieldError error={error} field="email" />
        </label>
        <label>
          6-digit code
          <input
            className="code-input"
            inputMode="numeric"
            autoComplete="one-time-code"
            pattern="\d{6}"
            maxLength={6}
            value={code}
            onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
            required
          />
          <FieldError error={error} field="code" />
        </label>
        <SubmitButton pending={pending} disabled={code.length !== 6}>
          Activate
        </SubmitButton>
      </form>
      <p className="auth-links">
        <button type="button" className="link-button" onClick={() => void resend()} disabled={resending || !email}>
          {resending ? 'Sending…' : 'Send a new code'}
        </button>
      </p>
    </section>
  );
}

// ---- Reset password -----------------------------------------------------------------------------------

export function PasswordResetPage() {
  const navigate = useNavigate();
  const [step, setStep] = useState<'email' | 'code'>('email');
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<unknown>(null);

  async function requestCode(e?: FormEvent) {
    e?.preventDefault();
    setPending(true);
    setError(null);
    try {
      await authApi.requestPasswordReset({ email });
      setStep('code');
    } catch (err) {
      setError(err);
    } finally {
      setPending(false);
    }
  }

  async function confirm(e?: FormEvent) {
    e?.preventDefault();
    setPending(true);
    setError(null);
    try {
      await authApi.confirmPasswordReset({ email, code, newPassword });
      navigate(`/sign-in?notice=reset&email=${encodeURIComponent(email.trim())}`);
    } catch (err) {
      setError(err);
    } finally {
      setPending(false);
    }
  }

  const banner = isCode(error, 'LOCKED_OUT') ? (
    <div className="error-banner" role="alert">{lockoutMessage(error)}</div>
  ) : (
    <ErrorBanner error={bannerError(error)} onRetry={() => void (step === 'email' ? requestCode() : confirm())} />
  );

  if (step === 'email') {
    return (
      <section className="auth-card">
        <h1>Reset password</h1>
        {banner}
        <form onSubmit={requestCode} className="auth-form" noValidate>
          <label>
            Email
            <input type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
            <FieldError error={error} field="email" />
          </label>
          <SubmitButton pending={pending}>Send reset code</SubmitButton>
        </form>
        <p className="auth-links">
          <Link to="/sign-in">Back to sign in</Link>
        </p>
      </section>
    );
  }

  return (
    <section className="auth-card">
      <h1>Choose a new password</h1>
      {/* Same message whether or not the email has an account. */}
      <p className="auth-notice">If an account exists for {email}, we emailed it a 6-digit code.</p>
      {banner}
      <form onSubmit={confirm} className="auth-form" noValidate>
        <label>
          6-digit code
          <input
            className="code-input"
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength={6}
            value={code}
            onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
            required
          />
          <FieldError error={error} field="code" />
        </label>
        <label>
          New password
          <input type="password" autoComplete="new-password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} required />
          <span className="muted small">At least 8 characters, with a letter and a number.</span>
          <FieldError error={error} field="newPassword" />
        </label>
        <SubmitButton pending={pending} disabled={code.length !== 6}>
          Change password
        </SubmitButton>
      </form>
      <p className="auth-links">
        <button type="button" className="link-button" onClick={() => setStep('email')}>
          Use a different email or resend
        </button>
      </p>
    </section>
  );
}

// ---- Profile ------------------------------------------------------------------------------------------

export function ProfilePage() {
  const { me, setMe } = useAuth();
  const [profile, setProfile] = useState<User | null>(null);
  const [displayName, setDisplayName] = useState('');
  const [loadError, setLoadError] = useState<unknown>(null);
  const [error, setError] = useState<unknown>(null);
  const [conflict, setConflict] = useState<User | null>(null);
  const [pending, setPending] = useState(false);
  const [saved, setSaved] = useState(false);

  const load = useCallback(() => {
    setLoadError(null);
    usersApi
      .getProfile()
      .then((u) => {
        setProfile(u);
        setDisplayName(u.displayName);
      })
      .catch(setLoadError);
  }, []);

  useEffect(load, [load]);

  async function submit(e?: FormEvent) {
    e?.preventDefault();
    if (!profile) return;
    setPending(true);
    setError(null);
    setSaved(false);
    try {
      const updated = await usersApi.updateProfile({ displayName, version: profile.version });
      setProfile(updated);
      setDisplayName(updated.displayName);
      if (me) setMe({ ...me, user: updated });
      setSaved(true);
    } catch (err) {
      if (err instanceof ApiError && err.isVersionConflict) setConflict(err.current as User);
      else setError(err);
    } finally {
      setPending(false);
    }
  }

  if (loadError) return <ErrorBanner error={loadError} onRetry={load} />;
  if (!profile) return <p className="muted">Loading…</p>;

  return (
    <section className="panel">
      <h1>Profile</h1>
      <dl className="profile-facts">
        <dt>Email</dt>
        <dd>{profile.email}</dd>
        <dt>Account type</dt>
        <dd>{profile.systemRole === 'SYSTEM_ADMIN' ? 'System admin' : 'User'}</dd>
        <dt>Member since</dt>
        <dd>{new Date(profile.createdAt).toLocaleDateString()}</dd>
      </dl>
      <ErrorBanner error={bannerError(error)} onRetry={() => void submit()} />
      {saved && <p className="auth-notice">Saved.</p>}
      <form onSubmit={submit} className="auth-form" noValidate>
        <label>
          Display name
          <input maxLength={60} value={displayName} onChange={(e) => setDisplayName(e.target.value)} required />
          <FieldError error={error} field="displayName" />
        </label>
        <SubmitButton pending={pending} disabled={displayName.trim() === profile.displayName}>
          Save
        </SubmitButton>
      </form>
      {conflict && (
        <ConflictDialog
          latest={conflict}
          myChanges={displayName}
          onReloadLatest={(latest) => {
            setProfile(latest);
            setDisplayName(latest.displayName);
            setConflict(null);
          }}
          onClose={() => setConflict(null)}
        />
      )}
    </section>
  );
}

// ---- Admin: all users ---------------------------------------------------------------------------------

export function AdminUsersPage() {
  const { isSystemAdmin, me } = useAuth();
  const [page, setPage] = useState(1);
  const [data, setData] = useState<Page<User> | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [savingId, setSavingId] = useState<number | null>(null);

  const load = useCallback(() => {
    setError(null);
    usersApi.list(page).then(setData).catch(setError);
  }, [page]);

  useEffect(load, [load]);

  async function changeRole(user: User, systemRole: SystemRole) {
    setSavingId(user.userId);
    setError(null);
    try {
      const updated = await usersApi.changeSystemRole(user.userId, { systemRole, version: user.version });
      setData((d) => d && { ...d, items: d.items.map((u) => (u.userId === updated.userId ? updated : u)) });
    } catch (err) {
      setError(err);
      if (err instanceof ApiError && err.isVersionConflict) load();
    } finally {
      setSavingId(null);
    }
  }

  if (me && !isSystemAdmin) return <p>Only system admins can manage accounts.</p>;

  const lastPage = data ? Math.max(1, Math.ceil(data.total / data.pageSize)) : 1;

  return (
    <section className="panel">
      <h1>All users</h1>
      <ErrorBanner error={error} onRetry={load} />
      {!data ? (
        <p className="muted">Loading…</p>
      ) : (
        <>
          <table className="users-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Email</th>
                <th>Status</th>
                <th>Joined</th>
                <th>System role</th>
              </tr>
            </thead>
            <tbody>
              {data.items.map((u) => (
                <tr key={u.userId}>
                  <td>{u.displayName}</td>
                  <td>{u.email}</td>
                  <td>{u.activated ? 'Active' : 'Not activated'}</td>
                  <td>{new Date(u.createdAt).toLocaleDateString()}</td>
                  <td>
                    <select
                      aria-label={`System role for ${u.displayName}`}
                      value={u.systemRole}
                      disabled={savingId === u.userId || u.userId === me?.user.userId}
                      onChange={(e) => void changeRole(u, e.target.value as SystemRole)}
                    >
                      <option value="USER">User</option>
                      <option value="SYSTEM_ADMIN">System admin</option>
                    </select>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="pager">
            <button type="button" disabled={page <= 1} onClick={() => setPage(page - 1)}>
              Previous
            </button>
            <span className="muted">
              Page {data.page} of {lastPage} · {data.total} accounts
            </span>
            <button type="button" disabled={page >= lastPage} onClick={() => setPage(page + 1)}>
              Next
            </button>
          </div>
        </>
      )}
    </section>
  );
}
