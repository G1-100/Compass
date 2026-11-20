// Auth and account pages. Owner: Gavin.
import { useState, type FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { setDevUserId } from '../../api/client';
import { TodoPage } from '../../components/TodoPage';
import styles from './AuthPages.module.css';

export function SignInPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [devUser, setDevUser] = useState('2');
  const [error, setError] = useState('');
  const devAuthAvailable = import.meta.env.DEV;
  const from = (location.state as { from?: string } | null)?.from ?? '/teams';

  function signInAsDeveloper(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    try {
      setDevUserId(devUser);
      navigate(from, { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not start developer session');
    }
  }

  if (devAuthAvailable) {
    return (
      <main className={styles.signInPage}>
        <section className={styles.signInCard}>
          <div className={styles.brandMark} aria-hidden="true">C</div>
          <h1>Welcome to Compass</h1>
          <p className={styles.description}>Sign in to continue to your workspace.</p>
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
            <button type="submit" className={styles.submitButton}>Continue to Compass</button>
          </form>
        </section>
      </main>
    );
  }

  return (
    <TodoPage
      title="Sign in"
      owner="Gavin"
      api={['authApi.login', 'authApi.loginWithGoogle']}
      todos={[
        'Email + password form; on success setMe(result) and go to the page the user came from',
        'Show "Incorrect email or password." on 401 and the lockout message + Retry-After on 429',
        '"Sign in with Google" button using VITE_GOOGLE_CLIENT_ID (Google Identity Services pop-up)',
        'Links to Register and Forgot password',
      ]}
    />
  );
}

export function RegisterPage() {
  return (
    <TodoPage
      title="Create account"
      owner="Gavin"
      api={['authApi.register']}
      todos={['Email, display name, password (8+ chars, a letter and a number)', 'Show field errors', 'Then go to /activate']}
    />
  );
}

export function ActivatePage() {
  return (
    <TodoPage
      title="Activate account"
      owner="Gavin"
      api={['authApi.activate', 'authApi.resendCode']}
      todos={['6-digit code input', 'Resend code button (lockout applies)', 'On success go to /sign-in']}
    />
  );
}

export function PasswordResetPage() {
  return (
    <TodoPage
      title="Reset password"
      owner="Gavin"
      api={['authApi.requestPasswordReset', 'authApi.confirmPasswordReset']}
      todos={['Step 1: email', 'Step 2: code + new password', 'Same message whether or not the email exists']}
    />
  );
}

export function ProfilePage() {
  return (
    <TodoPage title="Profile" owner="Gavin" api={['usersApi.getProfile', 'usersApi.updateProfile']} todos={['Edit display name (version-checked)']} />
  );
}

export function AdminUsersPage() {
  return (
    <TodoPage
      title="All users (admin)"
      owner="Gavin"
      api={['usersApi.list', 'usersApi.changeSystemRole']}
      todos={['Paged list of accounts', 'Change system role (SYSTEM_ADMIN only)']}
    />
  );
}
