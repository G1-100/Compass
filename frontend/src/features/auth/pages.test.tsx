import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { authApi } from '../../api/auth';
import { ApiError } from '../../api/client';
import type { MeResponse } from '../../api/types';
import { AuthProvider } from '../../auth/AuthContext';
import { SignInPage, lockoutMessage } from './pages';

const me: MeResponse = {
  user: { userId: 2, email: 'lead@usc.edu', displayName: 'Lena Lead', systemRole: 'USER', activated: true, createdAt: '2026-11-01T00:00:00Z', version: 1 },
  teams: [],
};

function renderSignIn() {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={['/sign-in']}>
        <Routes>
          <Route path="/sign-in" element={<SignInPage />} />
          <Route path="/teams" element={<p>Teams page</p>} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  );
}

function fillAndSubmit() {
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'lead@usc.edu' } });
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'Password1' } });
  fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));
}

afterEach(() => vi.restoreAllMocks());

describe('SignInPage', () => {
  it('signs in and goes to the teams page', async () => {
    vi.spyOn(authApi, 'me').mockRejectedValue(new ApiError(401, 'UNAUTHENTICATED', 'Please sign in.'));
    const login = vi.spyOn(authApi, 'login').mockResolvedValue(me);
    renderSignIn();
    fillAndSubmit();
    expect(await screen.findByText('Teams page')).toBeInTheDocument();
    expect(login).toHaveBeenCalledWith({ email: 'lead@usc.edu', password: 'Password1' });
  });

  it('shows the generic message on 401', async () => {
    vi.spyOn(authApi, 'me').mockRejectedValue(new ApiError(401, 'UNAUTHENTICATED', 'Please sign in.'));
    vi.spyOn(authApi, 'login').mockRejectedValue(new ApiError(401, 'UNAUTHENTICATED', 'Incorrect email or password.'));
    renderSignIn();
    fillAndSubmit();
    expect(await screen.findByRole('alert')).toHaveTextContent('Incorrect email or password.');
    await waitFor(() => expect(screen.getByLabelText('Password')).toHaveValue(''));
  });

  it('shows the lockout message with the remaining minutes on 429', async () => {
    vi.spyOn(authApi, 'me').mockRejectedValue(new ApiError(401, 'UNAUTHENTICATED', 'Please sign in.'));
    vi.spyOn(authApi, 'login').mockRejectedValue(
      new ApiError(429, 'LOCKED_OUT', 'Too many login attempts. Try again in 30 minutes.', {}, undefined, 600),
    );
    renderSignIn();
    fillAndSubmit();
    expect(await screen.findByRole('alert')).toHaveTextContent('Too many login attempts. Try again in 10 minutes.');
  });
});

describe('lockoutMessage', () => {
  it('rounds up to whole minutes', () => {
    expect(lockoutMessage(new ApiError(429, 'LOCKED_OUT', 'Too many login attempts. Try again in 30 minutes.', {}, undefined, 61)))
      .toBe('Too many login attempts. Try again in 2 minutes.');
    expect(lockoutMessage(new ApiError(429, 'LOCKED_OUT', 'Too many code requests. Try again in 30 minutes.', {}, undefined, 30)))
      .toBe('Too many code requests. Try again in 1 minute.');
  });
});
