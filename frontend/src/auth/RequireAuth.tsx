import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';
import { getDevUserId } from '../api/client';
import { useAuth } from './AuthContext';

/**
 * Wraps signed-in pages. Redirects to /sign-in when signed out.
 * In dev with VITE_DEV_USER_ID set, pages render without signing in (the backend trusts X-Dev-User-Id).
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { me, loading } = useAuth();
  const location = useLocation();
  const devBypass = !!getDevUserId();

  if (loading) return <p className="muted">Loading…</p>;
  if (!me && !devBypass) return <Navigate to="/sign-in" replace state={{ from: location.pathname }} />;
  return <>{children}</>;
}
