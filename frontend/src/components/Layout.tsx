import { NavLink, Outlet } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { NotificationBell } from '../features/notifications/NotificationBell';
import styles from './Layout.module.css';

/** App shell for signed-in pages: top nav + page content. Owner: Adam (frontend lead). */
export function Layout() {
  const { me, signOut } = useAuth();
  return (
    <div className="app">
      <header className={styles.topbar}>
        <NavLink to="/" className={styles.brand} aria-label="Compass home">
          <span className={styles.brandMark} aria-hidden="true">
            C
          </span>
          Compass
        </NavLink>
        <nav className={styles.primaryNav} aria-label="Primary navigation">
          <NavLink to="/teams">Teams</NavLink>
          <NavLink to="/my-tasks">My tasks</NavLink>
          <NavLink to="/invitations">Invitations</NavLink>
        </nav>
        <span className={styles.experimentalHeader}>Experimental Header</span>
        <div className={styles.topbarRight}>
          <NotificationBell />
          <NavLink to="/profile" className={styles.profile}>
            {me?.user.displayName ?? 'Profile'}
          </NavLink>
          <button type="button" className={styles.signOut} onClick={() => void signOut()}>
            Sign out
          </button>
        </div>
      </header>
      <main>
        <Outlet />
      </main>
    </div>
  );
}
