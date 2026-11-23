/**
 * Holds the signed-in user for the whole app. Owner: Gavin.
 * On load it calls GET /api/auth/me, so a valid session cookie restores the user after a refresh.
 * Any 401 from any API call clears the user; RequireAuth then redirects to /sign-in.
 */
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { authApi } from '../api/auth';
import { setUnauthenticatedHandler } from '../api/client';
import type { MeResponse, TeamRole } from '../api/types';

interface AuthState {
  /** null = signed out (or not loaded yet — check `loading`). */
  me: MeResponse | null;
  loading: boolean;
  /** Called after a successful login/register so the app re-renders as signed in. */
  setMe: (me: MeResponse | null) => void;
  signOut: () => Promise<void>;
  /** The user's role in a team, or null if not a member. */
  roleIn: (teamId: number) => TeamRole | null;
  isSystemAdmin: boolean;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<MeResponse | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    authApi
      .me()
      .then((result) => {
        if (!cancelled) setMe(result);
      })
      .catch(() => {
        if (!cancelled) setMe(null);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    // Any 401 from any API call clears the user; RequireAuth then redirects to /sign-in.
    setUnauthenticatedHandler(() => setMe(null));
    return () => setUnauthenticatedHandler(null);
  }, []);

  const signOut = useCallback(async () => {
    try {
      await authApi.logout();
    } catch {
      // Already signed out on the server (expired session); clear the local state anyway.
    }
    setMe(null);
  }, []);

  const value = useMemo<AuthState>(
    () => ({
      me,
      loading,
      setMe,
      signOut,
      roleIn: (teamId) => me?.teams.find((t) => t.teamId === teamId)?.role ?? null,
      isSystemAdmin: me?.user.systemRole === 'SYSTEM_ADMIN',
    }),
    [me, loading, signOut],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
