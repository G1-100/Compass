// Shared task helpers, labels and design-preview data. Owner: Adam.
import { useEffect, useState } from 'react';
import { ApiError, getDevUserId } from '../../api/client';
import { projectsApi } from '../../api/projects';
import { milestonesApi, sprintsApi } from '../../api/sprints';
import { teamsApi } from '../../api/teams';
import type {
  ActivityEntry,
  Comment,
  EffortLevel,
  LateRiskStatus,
  LocalDate,
  Membership,
  MilestoneView,
  Project,
  Sprint,
  Task,
  TaskStatus,
} from '../../api/types';
import { useAuth } from '../../auth/AuthContext';

// ---- Labels ----

export const STATUS_LABEL: Record<TaskStatus, string> = {
  NOT_STARTED: 'Not started',
  IN_PROGRESS: 'In progress',
  BLOCKED: 'Blocked',
  DONE: 'Done',
};
export const STATUS_ORDER: TaskStatus[] = ['NOT_STARTED', 'IN_PROGRESS', 'BLOCKED', 'DONE'];

export const EFFORT_LABEL: Record<EffortLevel, string> = {
  MINOR: 'Minor',
  MODERATE: 'Moderate',
  HIGH: 'High',
  EXTREME: 'Extreme',
};
export const EFFORT_ORDER: EffortLevel[] = ['MINOR', 'MODERATE', 'HIGH', 'EXTREME'];

/** Short pill shown in the Risk column; null renders an em dash. */
export function riskBadge(risk: LateRiskStatus): { label: string; tone: 'overdue' | 'blocker' | 'capacity'; icon: boolean } | null {
  switch (risk) {
    case 'OVERDUE':
      return { label: 'Overdue', tone: 'overdue', icon: false };
    case 'AT_RISK_UNRESOLVED_BLOCKER':
      return { label: 'Blocker', tone: 'blocker', icon: true };
    case 'AT_RISK_INSUFFICIENT_CAPACITY':
      return { label: 'Capacity', tone: 'capacity', icon: true };
    default:
      return null;
  }
}

/** Longer sentence for the banner at the top of the task panel. */
export function riskMessage(task: Task): string | null {
  const note = PREVIEW_RISK_NOTES[task.taskId];
  switch (task.lateRisk) {
    case 'OVERDUE':
      return `Overdue: this task was due ${task.dueDate ? formatDate(task.dueDate, true) : 'earlier'} and isn't done yet`;
    case 'AT_RISK_UNRESOLVED_BLOCKER':
      return note ?? 'At risk: an unresolved blocker from a check-in is linked to this task';
    case 'AT_RISK_INSUFFICIENT_CAPACITY':
      return note ?? "At risk: assignees don't have enough capacity before the due date";
    default:
      return null;
  }
}

// ---- Formatting ----

export function parseDate(value: LocalDate): Date {
  const [y, m, d] = value.split('-').map(Number);
  return new Date(y, m - 1, d);
}

export function formatDate(value: LocalDate, withYear = false): string {
  return parseDate(value).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    ...(withYear ? { year: 'numeric' } : {}),
  });
}

export function timeAgo(instant: string): string {
  const seconds = Math.max(0, (Date.now() - new Date(instant).getTime()) / 1000);
  if (seconds < 60) return 'now';
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}h`;
  if (seconds < 86400 * 7) return `${Math.floor(seconds / 86400)}d`;
  return new Date(instant).toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}

/** "Adam" -> "AD", "Graham" -> "GR", "Liv" -> "LV", "Ada Lovelace" -> "AL". */
export function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  if (parts.length > 1) return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  const word = parts[0];
  const consonant = word.slice(1).match(/[^aeiou\W\d_]/i)?.[0] ?? word[1] ?? '';
  return (word[0] + consonant).toUpperCase();
}

export function sprintName(sprints: Sprint[], sprintId: number | null): string {
  if (sprintId === null) return 'Backlog';
  return sprints.find((s) => s.sprintId === sprintId)?.name ?? `Sprint #${sprintId}`;
}

export function errorText(err: unknown, fallback: string): string {
  return err instanceof Error && err.message ? `${fallback}: ${err.message}` : fallback;
}

/** True when the backend route exists but isn't built yet (or the server isn't reachable in dev). */
export function shouldUsePreview(err: unknown): boolean {
  return err instanceof ApiError && (err.code === 'NOT_IMPLEMENTED' || err.code === 'NETWORK' || err.status === 404 || err.status >= 500);
}

// ---- Current user ----

export function useCurrentUser(): { userId: number; displayName: string } {
  const { me } = useAuth();
  if (me) return { userId: me.user.userId, displayName: me.user.displayName };
  const devId = Number(getDevUserId() ?? PREVIEW_ME);
  return { userId: devId, displayName: PREVIEW_MEMBERS.find((m) => m.userId === devId)?.displayName ?? 'You' };
}

// ---- Project context (project, sprints, milestones, members) ----

export interface ProjectContext {
  project: Project | null;
  sprints: Sprint[];
  milestones: MilestoneView[];
  members: Membership[];
  preview: boolean;
}

const EMPTY_CONTEXT: ProjectContext = { project: null, sprints: [], milestones: [], members: [], preview: false };

export function useProjectContext(projectId: number | null): ProjectContext {
  const [context, setContext] = useState<ProjectContext>(EMPTY_CONTEXT);

  useEffect(() => {
    if (projectId === null || Number.isNaN(projectId)) return;
    let active = true;
    (async () => {
      let preview = false;
      const settle = async <T,>(call: () => Promise<T>, fallback: T): Promise<T> => {
        try {
          return await call();
        } catch (err) {
          if (!shouldUsePreview(err)) console.warn(err);
          preview = true;
          return fallback;
        }
      };
      const project = await settle<Project | null>(() => projectsApi.get(projectId), PREVIEW_PROJECT);
      const [sprints, milestones, members] = await Promise.all([
        settle(() => sprintsApi.list(projectId), PREVIEW_SPRINTS),
        settle(() => milestonesApi.list(projectId), PREVIEW_MILESTONES),
        project ? settle(() => teamsApi.listMembers(project.teamId), PREVIEW_MEMBERS) : Promise.resolve(PREVIEW_MEMBERS),
      ]);
      if (active) setContext({ project, sprints, milestones, members, preview });
    })();
    return () => {
      active = false;
    };
  }, [projectId]);

  return context;
}

// ---- Design preview data (shown until the tasks backend is implemented) ----

export const PREVIEW_ME = 1;

const now = Date.now();
const hoursAgo = (h: number) => new Date(now - h * 3600_000).toISOString();

export const PREVIEW_PROJECT: Project = {
  projectId: 1,
  teamId: 1,
  name: 'Compass',
  description: null,
  targetDate: '2026-12-12',
  status: 'ACTIVE',
  archivedAt: null,
  createdBy: 1,
  createdAt: '2026-09-01T00:00:00Z',
  version: 1,
};

export const PREVIEW_SPRINTS: Sprint[] = [
  { sprintId: 2, projectId: 1, name: 'Sprint 2', startDate: '2026-10-19', endDate: '2026-11-01', status: 'COMPLETED', completedAt: hoursAgo(450), version: 3 },
  { sprintId: 3, projectId: 1, name: 'Sprint 3', startDate: '2026-11-02', endDate: '2026-11-29', status: 'ACTIVE', completedAt: null, version: 2 },
  { sprintId: 4, projectId: 1, name: 'Sprint 4', startDate: '2026-11-30', endDate: '2026-12-12', status: 'PLANNED', completedAt: null, version: 1 },
];

const member = (userId: number, displayName: string, role: Membership['role'] = 'MEMBER'): Membership => ({
  membershipId: userId,
  teamId: 1,
  userId,
  displayName,
  role,
  joinedAt: '2026-09-01T00:00:00Z',
  version: 1,
});

export const PREVIEW_MEMBERS: Membership[] = [
  member(1, 'Adam', 'LEAD'),
  member(2, 'Graham'),
  member(3, 'Liv'),
  member(4, 'Solomon'),
  member(5, 'Siya'),
  member(6, 'Raegen'),
];

const person = (userId: number, allocationPercent: number | null = null) => ({
  userId,
  displayName: PREVIEW_MEMBERS.find((m) => m.userId === userId)?.displayName ?? `User ${userId}`,
  allocationPercent,
});

const task = (t: Partial<Task> & Pick<Task, 'taskId' | 'title'>): Task => ({
  projectId: 1,
  sprintId: 3,
  description: null,
  dueDate: null,
  effort: 'MODERATE',
  status: 'NOT_STARTED',
  suggestionStatus: null,
  suggestedBy: null,
  createdBy: 1,
  createdAt: hoursAgo(300),
  updatedAt: hoursAgo(5),
  version: 1,
  assignees: [],
  overdue: false,
  lateRisk: 'NO_RISK',
  ...t,
});

export const PREVIEW_TASKS: Task[] = [
  task({
    taskId: 101,
    title: 'Guest link revoke flow',
    description: 'Let leads revoke a guest link from the dashboard. Revoked links should show the "link unavailable" page.',
    dueDate: '2026-11-01',
    effort: 'HIGH',
    status: 'BLOCKED',
    assignees: [person(4), person(1)],
    overdue: true,
    lateRisk: 'OVERDUE',
  }),
  task({
    taskId: 102,
    title: 'Hook up auth API',
    description: 'Connect sign-in, sign-up and verify pages to /api/auth. Handle 401, 429 lockout and network timeouts.',
    dueDate: '2026-11-22',
    effort: 'HIGH',
    status: 'IN_PROGRESS',
    assignees: [person(1, 60), person(2, 40)],
    lateRisk: 'AT_RISK_UNRESOLVED_BLOCKER',
    version: 4,
  }),
  task({
    taskId: 103,
    title: 'Version conflict dialog',
    description: 'Show the latest saved record on 409 and keep the user’s edits in a copyable area.',
    dueDate: '2026-11-24',
    effort: 'MODERATE',
    status: 'NOT_STARTED',
    assignees: [person(1)],
    lateRisk: 'AT_RISK_INSUFFICIENT_CAPACITY',
  }),
  task({
    taskId: 104,
    title: 'Task board filters',
    description: 'Status, assignee, sprint, backlog, overdue and search filters with sorting.',
    dueDate: '2026-11-26',
    effort: 'MODERATE',
    status: 'IN_PROGRESS',
    assignees: [person(1)],
  }),
  task({
    taskId: 105,
    title: 'Empty states pass',
    dueDate: '2026-12-01',
    effort: 'MINOR',
    status: 'NOT_STARTED',
    sprintId: 4,
    assignees: [person(3), person(1)],
  }),
  task({
    taskId: 106,
    title: 'Sign-in page',
    dueDate: '2026-10-28',
    effort: 'MINOR',
    status: 'DONE',
    sprintId: 2,
    assignees: [person(1)],
  }),
  task({ taskId: 107, title: 'Check-in reminder emails', dueDate: '2026-11-25', effort: 'MODERATE', status: 'DONE', assignees: [person(5)] }),
  task({ taskId: 108, title: 'Workload chart', dueDate: '2026-11-27', effort: 'HIGH', status: 'NOT_STARTED', assignees: [person(3)] }),
  task({ taskId: 109, title: 'Notification preferences', dueDate: '2026-11-21', effort: 'HIGH', status: 'DONE', assignees: [person(6)] }),
  task({ taskId: 110, title: 'Dark mode tokens', sprintId: null, effort: 'MINOR' }),
  task({ taskId: 111, title: 'Accessibility audit', sprintId: null, effort: 'MODERATE' }),
  task({
    taskId: 112,
    title: 'Keyboard shortcuts for the board',
    description: 'j/k to move between rows, Enter to open, s to change status.',
    sprintId: null,
    effort: 'MINOR',
    suggestionStatus: 'PENDING',
    suggestedBy: 3,
    createdBy: 3,
    createdAt: hoursAgo(20),
  }),
  task({
    taskId: 113,
    title: 'Export tasks to CSV',
    sprintId: null,
    effort: 'MODERATE',
    suggestionStatus: 'PENDING',
    suggestedBy: 2,
    createdBy: 2,
    createdAt: hoursAgo(6),
  }),
];

const PREVIEW_RISK_NOTES: Record<number, string> = {
  102: 'At risk: unresolved HIGH blocker from Thursday’s check-in — “Notification endpoints not deployed”',
};

export const PREVIEW_MILESTONES: MilestoneView[] = [
  {
    milestone: { milestoneId: 1, projectId: 1, name: 'Auth & accounts', description: null, targetDate: '2026-11-28', createdAt: hoursAgo(600), version: 1 },
    progress: { progressPercent: 50, deadlineStatus: 'APPROACHING' },
    linkedTaskIds: [102, 106],
  },
  {
    milestone: { milestoneId: 2, projectId: 1, name: 'Task board MVP', description: null, targetDate: '2026-11-30', createdAt: hoursAgo(600), version: 1 },
    progress: { progressPercent: 20, deadlineStatus: 'ON_SCHEDULE' },
    linkedTaskIds: [103, 104, 105],
  },
];

export const PREVIEW_COMMENTS: Record<number, Comment[]> = {
  102: [
    { commentId: 1, taskId: 102, authorId: 1, authorName: 'Adam', commentText: 'Starting on the sign-in form wiring today.', createdAt: hoursAgo(26) },
    { commentId: 2, taskId: 102, authorId: 2, authorName: 'Graham', commentText: '@Adam lockout returns 429 with Retry-After now.', createdAt: hoursAgo(2) },
    { commentId: 3, taskId: 102, authorId: 1, authorName: 'Adam', commentText: 'Wiring the countdown into the sign-in error.', createdAt: hoursAgo(1) },
  ],
};

export const PREVIEW_ACTIVITY: Record<number, ActivityEntry[]> = {
  102: [
    { activityId: 1, taskId: 102, sprintId: null, actorUserId: 1, actorName: 'Adam', activityType: 'TASK_CREATED', oldValue: null, newValue: 'Hook up auth API', createdAt: hoursAgo(120) },
    { activityId: 2, taskId: 102, sprintId: null, actorUserId: 1, actorName: 'Adam', activityType: 'ASSIGNEES_CHANGED', oldValue: 'Adam', newValue: 'Adam 60%, Graham 40%', createdAt: hoursAgo(100) },
    { activityId: 3, taskId: 102, sprintId: null, actorUserId: 1, actorName: 'Adam', activityType: 'STATUS_CHANGED', oldValue: 'NOT_STARTED', newValue: 'IN_PROGRESS', createdAt: hoursAgo(30) },
  ],
};

// ---- Due-date grouping (Wise-style "Today / This week" list headers) ----

export type DueBucket = 'overdue' | 'earlier' | 'today' | 'week' | 'later' | 'none';

export const DUE_BUCKET_LABEL: Record<DueBucket, string> = {
  overdue: 'Overdue',
  earlier: 'Earlier',
  today: 'Due today',
  week: 'Next 7 days',
  later: 'Later',
  none: 'No due date',
};

const BUCKET_ORDER: DueBucket[] = ['overdue', 'earlier', 'today', 'week', 'later'];

export function dueBucket(task: Task, today = new Date()): DueBucket {
  if (task.overdue) return 'overdue';
  if (!task.dueDate) return 'none';
  const start = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  const days = Math.round((parseDate(task.dueDate).getTime() - start.getTime()) / 86_400_000);
  if (days < 0) return 'earlier';
  if (days === 0) return 'today';
  if (days <= 7) return 'week';
  return 'later';
}

/** Splits an already-sorted list into due-date groups, keeping each group's internal order. */
export function groupByDue(tasks: Task[], order: 'asc' | 'desc'): { bucket: DueBucket; tasks: Task[] }[] {
  const buckets = order === 'asc' ? BUCKET_ORDER : [...BUCKET_ORDER].reverse();
  return [...buckets, 'none' as const]
    .map((bucket) => ({ bucket, tasks: tasks.filter((t) => dueBucket(t) === bucket) }))
    .filter((group) => group.tasks.length > 0);
}

export function daysUntil(date: LocalDate, today = new Date()): number {
  const start = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  return Math.round((parseDate(date).getTime() - start.getTime()) / 86_400_000);
}
