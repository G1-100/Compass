// Auth and account pages. Owner: Gavin.
import { TodoPage } from '../../components/TodoPage';

export function SignInPage() {
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
