import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { checkinsApi } from '../../api/checkins';
import { ApiError } from '../../api/client';
import type { Checkin, CheckinUpdate, TeamRole } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { CheckinDetailPage, CheckinsPage } from './pages';

vi.mock('../../api/checkins', () => ({
  checkinsApi: {
    list: vi.fn(), schedule: vi.fn(), get: vi.fn(), update: vi.fn(), cancel: vi.fn(), complete: vi.fn(),
    updates: vi.fn(), submitMyUpdate: vi.fn(), resolveBlocker: vi.fn(),
  },
}));

vi.mock('../../api/client', async (original) => ({
  ...await original<typeof import('../../api/client')>(),
  getDevUserId: vi.fn(),
}));

vi.mock('../../auth/AuthContext', () => ({ useAuth: vi.fn() }));

const meeting: Checkin = {
  checkinId: 7, teamId: 1, description: 'Demo preparation', location: 'https://example.com/meeting',
  status: 'SCHEDULED', createdBy: 2, version: 4, createdAt: '2026-11-20T02:00:00Z',
  scheduledAt: '2026-11-23T03:20:56Z', completedAt: null,
};

const alex: CheckinUpdate = {
  updateId: 8, checkinId: 7, userId: 3, displayName: 'Alex Smith', completedWork: 'First draft',
  upcomingTasks: 'Write tests', submittedAt: '2026-11-21T20:00:00Z', version: 2,
  blockers: [{
    blockerId: 40, updateId: 8, description: 'Waiting for review', severity: 'MEDIUM', taskId: 2,
    resolvedAt: null, resolvedBy: null,
  }],
};

const jordan: CheckinUpdate = {
  ...alex, updateId: 9, userId: 4, displayName: 'Jordan Lee', completedWork: 'Team notes', version: 1,
  blockers: [{ ...alex.blockers[0], blockerId: 41, updateId: 9, description: 'Waiting for data' }],
};

function setRole(role: TeamRole) {
  const userId = role === 'LEAD' ? 2 : 3;
  vi.mocked(useAuth).mockReturnValue({
    me: {
      user: {
        userId, email: 'member@example.test', displayName: 'Team member', systemRole: 'USER',
        activated: true, createdAt: meeting.createdAt, version: 1,
      },
      teams: [{ teamId: 1, teamName: 'Project team', role }],
    },
    loading: false, setMe: vi.fn(), signOut: vi.fn(),
    roleIn: (teamId) => teamId === 1 ? role : null,
    isSystemAdmin: false,
  });
}

function goTo(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/teams/:teamId/checkins" element={<CheckinsPage />} />
        <Route path="/checkins/:checkinId" element={<CheckinDetailPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.resetAllMocks();
  setRole('MEMBER');
  vi.mocked(checkinsApi.get).mockResolvedValue(meeting);
  vi.mocked(checkinsApi.updates).mockResolvedValue([alex, jordan]);
});

describe('Check-ins list', () => {
  it('shows upcoming and past meetings in local time and opens their details', async () => {
    vi.mocked(checkinsApi.list).mockResolvedValueOnce([meeting])
      .mockResolvedValueOnce([{ ...meeting, description: 'Earlier check-in', status: 'COMPLETED' }]);
    goTo('/teams/1/checkins');

    const localTime = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' });
    const link = await screen.findByRole('link', { name: localTime.format(new Date(meeting.scheduledAt)) });
    expect(link).toHaveAttribute('href', '/checkins/7');
    expect(link.querySelector('time')).toHaveAttribute('datetime', meeting.scheduledAt);
    expect(screen.queryByRole('heading', { name: 'Schedule a check-in' })).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'Past' }));
    expect(await screen.findByText('Earlier check-in')).toBeInTheDocument();
    expect(checkinsApi.list).toHaveBeenNthCalledWith(1, 1, 'upcoming');
    expect(checkinsApi.list).toHaveBeenNthCalledWith(2, 1, 'past');
    fireEvent.click(screen.getByRole('link', { name: localTime.format(new Date(meeting.scheduledAt)) }));
    expect(await screen.findByRole('heading', { name: 'My progress update' })).toBeInTheDocument();
  });

  it('sends a lead’s local meeting time as UTC and disables duplicate submits', async () => {
    setRole('LEAD');
    vi.mocked(checkinsApi.list).mockResolvedValue([]);
    let finish!: (checkin: Checkin) => void;
    vi.mocked(checkinsApi.schedule).mockReturnValue(new Promise((resolve) => { finish = resolve; }));
    goTo('/teams/1/checkins');

    const input = '2040-01-21T09:30';
    fireEvent.change(screen.getByLabelText('Scheduled for (local time)'), { target: { value: input } });
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Future check-in' } });
    fireEvent.change(screen.getByLabelText('Location or meeting link'), { target: { value: 'Room 204' } });
    fireEvent.click(screen.getByRole('button', { name: 'Schedule check-in' }));
    const pending = screen.getByRole('button', { name: 'Saving…' });
    expect(pending).toBeDisabled();
    fireEvent.click(pending);
    expect(checkinsApi.schedule).toHaveBeenCalledExactlyOnceWith({
      teamId: 1, scheduledAt: new Date(input).toISOString(), description: 'Future check-in', location: 'Room 204',
    });

    finish(meeting);
    expect(await screen.findByRole('link', { name: 'View check-in' })).toHaveAttribute('href', '/checkins/7');
    expect(screen.getByLabelText('Description')).toHaveValue('');
  });
});

describe('Check-in detail', () => {
  it('sends the existing version and blocker ID and retains the draft through timeout and conflict', async () => {
    const newer = { ...alex, completedWork: 'Newer phone edit', version: 3 };
    vi.mocked(checkinsApi.submitMyUpdate)
      .mockRejectedValueOnce(new ApiError(0, 'TIMEOUT', 'Connection problem, try again'))
      .mockRejectedValue(new ApiError(409, 'VERSION_CONFLICT', 'Changed elsewhere', {}, newer));
    goTo('/checkins/7');

    const work = await screen.findByLabelText('Completed work');
    fireEvent.change(work, { target: { value: 'Laptop draft' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save progress' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Connection problem, try again');
    expect(work).toHaveValue('Laptop draft');
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }));

    let dialog = await screen.findByRole('dialog', { name: 'Version conflict' });
    expect(dialog).toHaveTextContent('Newer phone edit');
    expect((within(dialog).getByLabelText('Your unsaved changes') as HTMLTextAreaElement).value).toContain('Laptop draft');
    const request = {
      version: 2, completedWork: 'Laptop draft', upcomingTasks: 'Write tests',
      blockers: [{ blockerId: 40, description: 'Waiting for review', severity: 'MEDIUM', taskId: 2 }],
    };
    expect(checkinsApi.submitMyUpdate).toHaveBeenNthCalledWith(1, 7, request);
    expect(checkinsApi.submitMyUpdate).toHaveBeenNthCalledWith(2, 7, request);

    fireEvent.click(within(dialog).getByRole('button', { name: 'Close' }));
    expect(work).toHaveValue('Laptop draft');
    fireEvent.click(screen.getByRole('button', { name: 'Save progress' }));
    dialog = await screen.findByRole('dialog');
    fireEvent.click(within(dialog).getByRole('button', { name: 'Reload latest' }));
    expect(work).toHaveValue('Newer phone edit');
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('submits a new blocker without an ID, then retains the ID assigned by the server', async () => {
    vi.mocked(checkinsApi.updates).mockResolvedValue([]);
    vi.mocked(checkinsApi.submitMyUpdate).mockResolvedValue({ ...alex, version: 1 });
    goTo('/checkins/7');

    fireEvent.change(await screen.findByLabelText('Completed work'), { target: { value: 'First draft' } });
    fireEvent.click(screen.getByRole('button', { name: 'Add blocker' }));
    fireEvent.change(screen.getByLabelText('Blocker description'), { target: { value: 'Waiting for review' } });
    fireEvent.change(screen.getByLabelText('Severity'), { target: { value: 'MEDIUM' } });
    fireEvent.change(screen.getByLabelText('Linked task ID (optional)'), { target: { value: '2' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save progress' }));
    expect(await screen.findByText('Progress saved.')).toBeInTheDocument();
    expect(checkinsApi.submitMyUpdate).toHaveBeenNthCalledWith(1, 7, {
      version: 0, completedWork: 'First draft', upcomingTasks: '',
      blockers: [{ description: 'Waiting for review', severity: 'MEDIUM', taskId: 2 }],
    });

    fireEvent.change(screen.getByLabelText('Completed work'), { target: { value: 'Revised draft' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save progress' }));
    await waitFor(() => expect(checkinsApi.submitMyUpdate).toHaveBeenNthCalledWith(2, 7, {
      version: 1, completedWork: 'Revised draft', upcomingTasks: alex.upcomingTasks,
      blockers: [{ blockerId: 40, description: 'Waiting for review', severity: 'MEDIUM', taskId: 2 }],
    }));
    expect(await screen.findByText('Progress saved.')).toBeInTheDocument();
  });

  it('keeps closed updates read-only and lets members resolve only their own blockers', async () => {
    vi.mocked(checkinsApi.get).mockResolvedValue({ ...meeting, status: 'COMPLETED', completedAt: meeting.scheduledAt });
    vi.mocked(checkinsApi.resolveBlocker)
      .mockResolvedValue({ ...alex.blockers[0], resolvedAt: meeting.scheduledAt, resolvedBy: 3 });
    goTo('/checkins/7');

    expect(await screen.findByText('This check-in is closed. Progress updates are read-only.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Save progress' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Edit or reschedule' })).toBeNull();
    expect(screen.getByText('Team notes')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Resolve blocker' })).toHaveLength(1);
    fireEvent.click(screen.getByRole('button', { name: 'Resolve blocker' }));
    expect(await screen.findByText('Resolved')).toBeInTheDocument();
    expect(checkinsApi.resolveBlocker).toHaveBeenCalledWith(7, 40);
    expect(screen.queryByRole('button', { name: 'Resolve blocker' })).toBeNull();
  });

  it('waits for the latest team updates after closure before letting the lead resolve blockers', async () => {
    setRole('LEAD');
    vi.mocked(checkinsApi.complete).mockResolvedValue({ ...meeting, status: 'COMPLETED', version: 5 });
    let finishRefresh!: (updates: CheckinUpdate[]) => void;
    vi.mocked(checkinsApi.updates).mockResolvedValueOnce([alex, jordan])
      .mockReturnValueOnce(new Promise((resolve) => { finishRefresh = resolve; }));
    vi.mocked(checkinsApi.resolveBlocker)
      .mockResolvedValue({ ...alex.blockers[0], resolvedAt: meeting.scheduledAt, resolvedBy: 2 });
    goTo('/checkins/7');

    fireEvent.click(await screen.findByRole('button', { name: 'Mark complete' }));
    fireEvent.click(screen.getByRole('button', { name: 'Confirm completion' }));
    await screen.findByText('This check-in is closed. Progress updates are read-only.');
    const resolve = screen.getAllByRole('button', { name: 'Resolve blocker' })[0];
    expect(resolve).toBeDisabled();
    finishRefresh([alex, jordan]);
    await waitFor(() => expect(resolve).toBeEnabled());
    fireEvent.click(resolve);
    expect(await screen.findByText('Resolved')).toBeInTheDocument();
    expect(checkinsApi.resolveBlocker).toHaveBeenCalledExactlyOnceWith(7, 40);
  });

  it('preserves an existing empty field when saving an untouched update', async () => {
    const saved = { ...alex, upcomingTasks: null };
    vi.mocked(checkinsApi.updates).mockResolvedValue([saved]);
    vi.mocked(checkinsApi.submitMyUpdate).mockResolvedValue(saved);
    goTo('/checkins/7');

    expect(await screen.findByLabelText('Upcoming tasks')).toHaveValue('');
    fireEvent.click(screen.getByRole('button', { name: 'Save progress' }));
    expect(await screen.findByText('Progress saved.')).toBeInTheDocument();
    expect(checkinsApi.submitMyUpdate).toHaveBeenCalledWith(7, expect.objectContaining({
      version: saved.version, upcomingTasks: null,
    }));
  });

  it('edits meeting details without rounding its saved schedule to the displayed minute', async () => {
    setRole('LEAD');
    vi.mocked(checkinsApi.update).mockResolvedValue({ ...meeting, description: 'Updated meeting', version: 5 });
    goTo('/checkins/7');

    fireEvent.click(await screen.findByRole('button', { name: 'Edit or reschedule' }));
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Updated meeting' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save check-in details' }));
    await waitFor(() => expect(screen.queryByLabelText('Description')).not.toBeInTheDocument());
    expect(screen.getByText('Updated meeting')).toBeInTheDocument();
    expect(checkinsApi.update).toHaveBeenCalledWith(7, {
      version: 4, scheduledAt: undefined, description: 'Updated meeting', location: undefined,
    });
  });

  it.each([
    { button: 'Mark complete', confirm: 'Confirm completion', action: 'complete', status: 'COMPLETED' },
    { button: 'Cancel check-in', confirm: 'Confirm cancellation', action: 'cancel', status: 'CANCELED' },
  ] as const)('lets a lead $button and then view all progress', async ({ button, confirm, action, status }) => {
    setRole('LEAD');
    vi.mocked(checkinsApi[action]).mockResolvedValue({ ...meeting, status, version: 5 });
    vi.mocked(checkinsApi.updates).mockResolvedValueOnce([alex, jordan])
      .mockResolvedValueOnce([alex, { ...jordan, completedWork: 'Submitted just before closure' }]);
    goTo('/checkins/7');

    fireEvent.click(await screen.findByRole('button', { name: button }));
    fireEvent.click(screen.getByRole('button', { name: confirm }));
    expect(await screen.findByText('This check-in is closed. Progress updates are read-only.')).toBeInTheDocument();
    expect(checkinsApi[action]).toHaveBeenCalledWith(7, 4);
    expect(await screen.findByText('Submitted just before closure')).toBeInTheDocument();
    expect(screen.getByText('First draft')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Resolve blocker' })).toHaveLength(2);
  });

  it('keeps an unsaved draft available to copy if another user closes the meeting mid-save', async () => {
    vi.mocked(checkinsApi.get).mockResolvedValueOnce(meeting)
      .mockResolvedValueOnce({ ...meeting, status: 'CANCELED', version: 5 });
    vi.mocked(checkinsApi.submitMyUpdate).mockRejectedValue(new ApiError(409, 'INVALID_STATE', 'This check-in is closed.'));
    goTo('/checkins/7');

    fireEvent.change(await screen.findByLabelText('Completed work'), { target: { value: 'Unsaved laptop work' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save progress' }));
    expect(await screen.findByText('This check-in is closed. Progress updates are read-only.')).toBeInTheDocument();
    fireEvent.click(screen.getByText('View unsaved draft'));
    expect(screen.getByText(/Unsaved laptop work/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Save progress' })).toBeNull();
  });
});
