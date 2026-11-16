import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, api, buildUrl, setUnauthenticatedHandler } from './client';

function mockFetch(status: number, body: unknown, headers: Record<string, string> = {}) {
  return vi.fn().mockResolvedValue(new Response(JSON.stringify(body), { status, headers }));
}

afterEach(() => {
  vi.restoreAllMocks();
  vi.useRealTimers();
  setUnauthenticatedHandler(null);
});

describe('api client', () => {
  it('builds query strings and skips empty values', () => {
    expect(buildUrl('/api/x', { a: 1, b: '', c: undefined, d: true })).toBe('/api/x?a=1&d=true');
  });

  it('sends JSON with Content-Type on POST', async () => {
    const fetchMock = mockFetch(201, { id: 1 });
    vi.stubGlobal('fetch', fetchMock);
    await api.post('/api/teams', { name: 'A' });
    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers['Content-Type']).toBe('application/json');
    expect(init.body).toBe('{"name":"A"}');
  });

  it('turns error bodies into ApiError with fields and current record', async () => {
    vi.stubGlobal('fetch', mockFetch(409, { error: { code: 'VERSION_CONFLICT', message: 'changed', current: { version: 4 } } }));
    const err = (await api.patch('/api/tasks/1', { version: 3 }).catch((e: ApiError) => e)) as ApiError;
    expect(err).toBeInstanceOf(ApiError);
    expect(err.isVersionConflict).toBe(true);
    expect(err.current).toEqual({ version: 4 });
  });

  it('calls the unauthenticated handler on 401', async () => {
    const handler = vi.fn();
    setUnauthenticatedHandler(handler);
    vi.stubGlobal('fetch', mockFetch(401, { error: { code: 'UNAUTHENTICATED', message: 'Please sign in.' } }));
    await api.get('/api/auth/me').catch(() => undefined);
    expect(handler).toHaveBeenCalledOnce();
  });

  it('aborts after 15 seconds with a TIMEOUT error', async () => {
    vi.useFakeTimers();
    vi.stubGlobal(
      'fetch',
      vi.fn((_url: string, init: RequestInit) =>
        new Promise((_resolve, reject) => {
          init.signal?.addEventListener('abort', () => reject(Object.assign(new Error('aborted'), { name: 'AbortError' })));
        }),
      ),
    );
    const pending = api.get('/api/slow').catch((e: ApiError) => e);
    await vi.advanceTimersByTimeAsync(15_000);
    const err = (await pending) as ApiError;
    expect(err.code).toBe('TIMEOUT');
    expect(err.message).toBe('Connection problem, try again');
  });
});
