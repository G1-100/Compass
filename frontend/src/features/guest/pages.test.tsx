import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../api/client';
import { guestApi } from '../../api/guest';
import type { GuestLink, GuestProjectView } from '../../api/types';
import { GuestLinksPanel, GuestViewPage, localInputToInstant } from './pages';

vi.mock('../../api/guest', () => ({
  guestApi: { list: vi.fn(), create: vi.fn(), revoke: vi.fn(), view: vi.fn() },
}));
const api = vi.mocked(guestApi);

const link = (over: Partial<GuestLink> = {}): GuestLink => ({
  linkId: 1,
  projectId: 7,
  createdBy: 11,
  createdAt: '2026-11-20T10:00:00Z',
  expiresAt: null,
  revokedAt: null,
  status: 'ACTIVE',
  ...over,
});

const view: GuestProjectView = {
  projectName: 'Compass',
  summary: 'Team project tracker',
  overallProgressPercent: 37.5,
  milestones: [{ name: 'Beta', progressPercent: 50, deadlineStatus: 'APPROACHING' }],
  tasks: [
    { title: 'Write spec', status: 'DONE' },
    { title: 'Build API', status: 'IN_PROGRESS' },
  ],
  memberDisplayNames: ['Ada', 'Grace'],
};

function renderGuestView(token = 'abc') {
  return render(
    <MemoryRouter initialEntries={[`/guest/${token}`]}>
      <Routes>
        <Route path="/guest/:token" element={<GuestViewPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.resetAllMocks();
});

describe('GuestViewPage', () => {
  it('shows only the shared project fields', async () => {
    api.view.mockResolvedValue(view);
    renderGuestView('tok123');

    expect(await screen.findByRole('heading', { name: 'Compass' })).toBeInTheDocument();
    expect(api.view).toHaveBeenCalledWith('tok123');
    expect(screen.getByText('Team project tracker')).toBeInTheDocument();
    expect(screen.getByRole('progressbar', { name: 'Overall progress' })).toHaveAttribute('aria-valuenow', '37.5');
    expect(screen.getByText('1 of 2 tasks done')).toBeInTheDocument();
    expect(screen.getByText('Beta')).toBeInTheDocument();
    expect(screen.getByText('Due soon')).toBeInTheDocument();
    expect(screen.getByText('Build API')).toBeInTheDocument();
    expect(screen.getByText('In progress')).toBeInTheDocument();
    expect(screen.getByText('Grace')).toBeInTheDocument();
  });

  it('says the link has expired for GUEST_LINK_UNAVAILABLE, with no project data', async () => {
    api.view.mockRejectedValue(new ApiError(404, 'GUEST_LINK_UNAVAILABLE', 'This link has expired or is no longer available.'));
    renderGuestView();

    expect(await screen.findByRole('heading', { name: 'This link has expired' })).toBeInTheDocument();
    expect(screen.queryByText('Milestones')).not.toBeInTheDocument();
  });

  it('offers Retry on a connection problem', async () => {
    api.view.mockRejectedValueOnce(new ApiError(0, 'TIMEOUT', 'Connection problem, try again')).mockResolvedValueOnce(view);
    renderGuestView();

    fireEvent.click(await screen.findByRole('button', { name: 'Retry' }));
    expect(await screen.findByRole('heading', { name: 'Compass' })).toBeInTheDocument();
  });

  it('shows "Progress unavailable" when the project has no tasks', async () => {
    api.view.mockResolvedValue({ ...view, overallProgressPercent: null, tasks: [], milestones: [] });
    renderGuestView();

    expect(await screen.findByText('Progress unavailable')).toBeInTheDocument();
    expect(screen.getByText('No tasks yet.')).toBeInTheDocument();
  });
});

describe('GuestLinksPanel', () => {
  it('lists links with their status and only offers Revoke for active ones', async () => {
    api.list.mockResolvedValue([
      link({ linkId: 1 }),
      link({ linkId: 2, status: 'EXPIRED', expiresAt: '2026-11-21T10:00:00Z' }),
      link({ linkId: 3, status: 'REVOKED', revokedAt: '2026-11-21T10:00:00Z' }),
    ]);
    render(<GuestLinksPanel projectId={7} />);

    expect(await screen.findByText('Active')).toBeInTheDocument();
    expect(screen.getByText('Expired')).toBeInTheDocument();
    expect(screen.getByText('Revoked')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Revoke' })).toHaveLength(1);
    expect(api.list).toHaveBeenCalledWith(7);
  });

  it('creates a link and shows the share URL once', async () => {
    api.list.mockResolvedValue([]);
    api.create.mockResolvedValue({ link: link({ linkId: 9 }), shareUrl: 'https://compass.example.com/guest/secret' });
    render(<GuestLinksPanel projectId={7} />);
    await screen.findByText('No guest links yet.');

    fireEvent.click(screen.getByRole('button', { name: 'Create link' }));

    expect(await screen.findByLabelText('Share URL')).toHaveValue('https://compass.example.com/guest/secret');
    expect(api.create).toHaveBeenCalledWith(7, null);
    expect(screen.getByText('Active')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Done' }));
    expect(screen.queryByLabelText('Share URL')).not.toBeInTheDocument();
  });

  it('shows the server message next to the expiry field', async () => {
    api.list.mockResolvedValue([]);
    api.create.mockRejectedValue(
      new ApiError(400, 'VALIDATION_FAILED', 'Some fields need attention.', { expiresAt: 'Expires at must be in the future.' }),
    );
    render(<GuestLinksPanel projectId={7} />);
    await screen.findByText('No guest links yet.');

    fireEvent.click(screen.getByRole('button', { name: 'Create link' }));
    expect(await screen.findByText('Expires at must be in the future.')).toBeInTheDocument();
  });

  it('revokes after confirmation and reloads the list', async () => {
    api.list.mockResolvedValueOnce([link()]).mockResolvedValueOnce([link({ status: 'REVOKED', revokedAt: '2026-11-22T10:00:00Z' })]);
    api.revoke.mockResolvedValue({});
    render(<GuestLinksPanel projectId={7} />);

    fireEvent.click(await screen.findByRole('button', { name: 'Revoke' }));
    expect(api.revoke).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'Confirm revoke' }));

    await waitFor(() => expect(api.revoke).toHaveBeenCalledWith(7, 1));
    expect(await screen.findByText('Revoked')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Revoke' })).not.toBeInTheDocument();
  });
});

describe('localInputToInstant', () => {
  it('returns null for an empty input and an ISO instant otherwise', () => {
    expect(localInputToInstant('')).toBeNull();
    expect(localInputToInstant('2026-12-01T09:30')).toBe(new Date('2026-12-01T09:30').toISOString());
  });
});
