/**
 * The only place that calls fetch(). PLATFORM — complete.
 *
 * - Relative /api/ paths only (same origin; Vercel/Vite forward to the servlet).
 * - JSON in and out; Content-Type: application/json on every POST/PUT/PATCH.
 * - Aborts after 15 seconds with ApiError code "TIMEOUT" (show "Connection problem, try again").
 * - Non-2xx responses throw ApiError with the server's error code, message, field errors and,
 *   for 409 VERSION_CONFLICT, the latest saved record in `current`.
 * - 401 calls the registered onUnauthenticated handler (AuthContext sends the user to Sign In).
 */

export const REQUEST_TIMEOUT_MS = 15_000;

export type ErrorCode =
  | 'VALIDATION_FAILED'
  | 'UNAUTHENTICATED'
  | 'FORBIDDEN'
  | 'NOT_FOUND'
  | 'GUEST_LINK_UNAVAILABLE'
  | 'VERSION_CONFLICT'
  | 'INVALID_STATE'
  | 'LOCKED_OUT'
  | 'RATE_LIMITED'
  | 'SERVER_ERROR'
  | 'NOT_IMPLEMENTED'
  | 'TIMEOUT'
  | 'NETWORK';

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: ErrorCode,
    message: string,
    public readonly fields: Record<string, string> = {},
    public readonly current?: unknown,
    public readonly retryAfterSeconds?: number,
  ) {
    super(message);
    this.name = 'ApiError';
  }

  get isVersionConflict() {
    return this.code === 'VERSION_CONFLICT';
  }
}

let onUnauthenticated: (() => void) | null = null;
const DEV_USER_STORAGE_KEY = 'compass.devUserId';

/** AuthContext registers this so any 401 sends the user back to Sign In. */
export function setUnauthenticatedHandler(handler: (() => void) | null) {
  onUnauthenticated = handler;
}

export function getDevUserId(): string | undefined {
  if (!import.meta.env.DEV) return undefined;
  const configuredUserId = localStorage.getItem(DEV_USER_STORAGE_KEY) ?? import.meta.env.VITE_DEV_USER_ID;
  return configuredUserId && /^\d+$/.test(configuredUserId) ? configuredUserId : undefined;
}

export function setDevUserId(userId: string): void {
  if (!import.meta.env.DEV) throw new Error('Developer sign-in is available only in development');
  if (!/^\d+$/.test(userId)) throw new Error('A valid developer user ID is required');
  localStorage.setItem(DEV_USER_STORAGE_KEY, userId);
}

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
type Query = Record<string, string | number | boolean | null | undefined>;

export function buildUrl(path: string, query?: Query): string {
  if (!query) return path;
  const params = new URLSearchParams();
  for (const [k, v] of Object.entries(query)) {
    if (v !== undefined && v !== null && v !== '') params.set(k, String(v));
  }
  const qs = params.toString();
  return qs ? `${path}?${qs}` : path;
}

export async function request<T>(method: Method, path: string, body?: unknown, query?: Query): Promise<T> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

  const headers: Record<string, string> = { Accept: 'application/json' };
  if (method === 'POST' || method === 'PUT' || method === 'PATCH') {
    headers['Content-Type'] = 'application/json';
  }
  const devUser = getDevUserId();
  if (devUser) headers['X-Dev-User-Id'] = devUser;

  let response: Response;
  try {
    response = await fetch(buildUrl(path, query), {
      method,
      headers,
      credentials: 'same-origin',
      body: headers['Content-Type'] ? JSON.stringify(body ?? {}) : undefined,
      signal: controller.signal,
    });
  } catch (e) {
    const timedOut = (e as Error).name === 'AbortError';
    throw new ApiError(0, timedOut ? 'TIMEOUT' : 'NETWORK', 'Connection problem, try again');
  } finally {
    clearTimeout(timer);
  }

  const text = await response.text();
  const data = text ? JSON.parse(text) : {};

  if (!response.ok) {
    const err = data?.error ?? {};
    const retry = response.headers.get('Retry-After');
    if (response.status === 401) onUnauthenticated?.();
    throw new ApiError(
      response.status,
      (err.code as ErrorCode) ?? 'SERVER_ERROR',
      err.message ?? 'Something went wrong. Please try again.',
      err.fields ?? {},
      err.current,
      retry ? Number(retry) : undefined,
    );
  }
  return data as T;
}

export const api = {
  get: <T>(path: string, query?: Query) => request<T>('GET', path, undefined, query),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body),
  put: <T>(path: string, body?: unknown) => request<T>('PUT', path, body),
  patch: <T>(path: string, body?: unknown) => request<T>('PATCH', path, body),
  delete: <T = Record<string, never>>(path: string) => request<T>('DELETE', path),
};
