import { NavLink, Outlet } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { NotificationBell } from '../features/notifications/NotificationBell';

/** App shell for signed-in pages: top nav + page content. Owner: Adam (frontend lead). */
export function Layout() {
  const { me, signOut } = useAuth();
  return (
    <div className="app">
      <header className="topbar">
        <NavLink to="/" className="brand">
          Compass
        </NavLink>
        <nav>
          <NavLink to="/teams">Teams</NavLink>
          <NavLink to="/my-tasks">My tasks</NavLink>
          <NavLink to="/invitations">Invitations</NavLink>
        </nav>
        <div className="topbar-right">
          <NotificationBell />
          <NavLink to="/profile">{me?.user.displayName ?? 'Profile'}</NavLink>
          <button type="button" onClick={() => void signOut()}>
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
