/**
 * TypeScript mirrors of the backend's JSON shapes. SHARED CONTRACT — when a Java record changes, change it here
 * in the same PR. Timestamps are ISO-8601 UTC strings; dates are "YYYY-MM-DD"; ids are numbers.
 */

export type Instant = string;
export type LocalDate = string;

export interface Page<T> {
  items: T[];
  page: number;
  pageSize: number;
  total: number;
}

// ---- Auth (Gavin) ----
export type SystemRole = 'USER' | 'SYSTEM_ADMIN';
export interface User {
  userId: number;
  email: string;
  displayName: string;
  systemRole: SystemRole;
  activated: boolean;
  createdAt: Instant;
  version: number;
}
export interface TeamMembershipSummary {
  teamId: number;
  teamName: string;
  role: TeamRole;
}
export interface MeResponse {
  user: User;
  teams: TeamMembershipSummary[];
}

// ---- Teams (Solomon) ----
export type TeamRole = 'MEMBER' | 'LEAD';
export interface Team {
  teamId: number;
  name: string;
  createdBy: number;
  createdAt: Instant;
  version: number;
}
export interface TeamWithRole {
  team: Team;
  myRole: TeamRole | null;
}
export interface Membership {
  membershipId: number;
  teamId: number;
  userId: number;
  displayName: string;
  role: TeamRole;
  joinedAt: Instant;
  version: number;
}
export type InvitationStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'REVOKED' | 'EXPIRED';
export interface Invitation {
  invitationId: number;
  teamId: number;
  teamName: string;
  inviteeEmail: string;
  role: TeamRole;
  status: InvitationStatus;
  invitedBy: number;
  createdAt: Instant;
  expiresAt: Instant;
  respondedAt: Instant | null;
}
export type AuditEventType = 'ROLE_CHANGED' | 'MEMBER_REMOVED' | 'MEMBER_LEFT' | 'GUEST_LINK_CREATED' | 'GUEST_LINK_REVOKED';
export interface AuditEvent {
  auditId: number;
  teamId: number;
  actorUserId: number | null;
  targetUserId: number | null;
  eventType: AuditEventType;
  details: string | null;
  createdAt: Instant;
}

// ---- Projects (Solomon) ----
export type ProjectStatus = 'PLANNING' | 'ACTIVE' | 'ON_HOLD' | 'COMPLETED';
export interface Project {
  projectId: number;
  teamId: number;
  name: string;
  description: string | null;
  targetDate: LocalDate | null;
  status: ProjectStatus;
  archivedAt: Instant | null;
  createdBy: number;
  createdAt: Instant;
  version: number;
}
export interface MemberAssignment {
  userId: number;
  displayName: string;
  activeTaskCount: number;
  activePoints: number;
}
export interface DashboardView {
  project: Project;
  progressPercent: number | null;
  teamAssignments: MemberAssignment[];
  upcomingDeadlines: Task[];
  milestones: MilestoneView[];
  myTasks: Task[];
}

// ---- Tasks (Adam) ----
export type TaskStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'BLOCKED' | 'DONE';
export type EffortLevel = 'MINOR' | 'MODERATE' | 'HIGH' | 'EXTREME';
export const EFFORT_POINTS: Record<EffortLevel, number> = { MINOR: 1, MODERATE: 3, HIGH: 5, EXTREME: 8 };
export type SuggestionStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export interface Assignee {
  userId: number;
  displayName: string;
  allocationPercent: number | null;
}
export interface Task {
  taskId: number;
  projectId: number;
  sprintId: number | null;
  title: string;
  description: string | null;
  dueDate: LocalDate | null;
  effort: EffortLevel;
  status: TaskStatus;
  suggestionStatus: SuggestionStatus | null;
  suggestedBy: number | null;
  createdBy: number;
  createdAt: Instant;
  updatedAt: Instant;
  version: number;
  assignees: Assignee[];
  overdue: boolean;
  lateRisk: LateRiskStatus;
}
export interface Comment {
  commentId: number;
  taskId: number;
  authorId: number | null;
  authorName: string;
  commentText: string;
  createdAt: Instant;
}
export interface ActivityEntry {
  activityId: number;
  taskId: number | null;
  sprintId: number | null;
  actorUserId: number | null;
  actorName: string | null;
  activityType: string;
  oldValue: string | null;
  newValue: string | null;
  createdAt: Instant;
}
export interface TaskFilter {
  status?: TaskStatus;
  assigneeId?: number;
  sprintId?: number;
  backlog?: boolean;
  overdue?: boolean;
  q?: string;
  sort?: 'dueDate' | 'effort' | 'sprint';
  order?: 'asc' | 'desc';
  page?: number;
  pageSize?: number;
}

// ---- Sprints and milestones (Siya) ----
export type SprintStatus = 'PLANNED' | 'ACTIVE' | 'COMPLETED';
export interface Sprint {
  sprintId: number;
  projectId: number;
  name: string;
  startDate: LocalDate;
  endDate: LocalDate;
  status: SprintStatus;
  completedAt: Instant | null;
  version: number;
}
export interface SprintCompletionResult {
  sprint: Sprint;
  targetSprintId: number | null;
  completedTaskIds: number[];
  movedTaskIds: number[];
}
export type DeadlineStatus = 'COMPLETE' | 'OVERDUE' | 'APPROACHING' | 'ON_SCHEDULE';
export interface Milestone {
  milestoneId: number;
  projectId: number;
  name: string;
  description: string | null;
  targetDate: LocalDate;
  createdAt: Instant;
  version: number;
}
export interface MilestoneView {
  milestone: Milestone;
  progress: { progressPercent: number | null; deadlineStatus: DeadlineStatus };
  linkedTaskIds: number[];
}

// ---- Check-ins (Graham) ----
export type CheckinStatus = 'SCHEDULED' | 'COMPLETED' | 'CANCELED';
export type Severity = 'LOW' | 'MEDIUM' | 'HIGH';
export interface Checkin {
  checkinId: number;
  teamId: number;
  description: string | null;
  scheduledAt: Instant;
  location: string | null;
  status: CheckinStatus;
  createdBy: number;
  createdAt: Instant;
  completedAt: Instant | null;
  version: number;
}
export interface Blocker {
  blockerId: number;
  updateId: number;
  description: string;
  severity: Severity;
  taskId: number | null;
  resolvedAt: Instant | null;
  resolvedBy: number | null;
}
export interface CheckinUpdate {
  updateId: number;
  checkinId: number;
  userId: number;
  displayName: string;
  completedWork: string | null;
  upcomingTasks: string | null;
  submittedAt: Instant;
  version: number;
  blockers: Blocker[];
}

export interface CheckinBlockerInput {
  blockerId?: Blocker['blockerId'];
  description: string;
  severity: Severity;
  taskId?: Blocker['taskId'];
}

export interface SubmitCheckinUpdate {
  version: CheckinUpdate['version'];
  completedWork: CheckinUpdate['completedWork'];
  upcomingTasks: CheckinUpdate['upcomingTasks'];
  blockers: CheckinBlockerInput[];
}

// ---- Workload (Liv) ----
export interface Availability {
  projectId: number;
  userId: number;
  hoursPerWeek: number;
  reportsOverload: boolean;
  updatedAt: Instant;
}
export type WorkloadFlag = 'OVERLOAD' | 'AVAILABLE_CAPACITY' | 'BALANCED';
export interface MemberWorkload {
  userId: number;
  displayName: string;
  assignedPoints: number;
  assignedShare: number;
  expectedShare: number;
  flag: WorkloadFlag;
}
export interface WorkloadResult {
  insufficientData: boolean;
  members: MemberWorkload[];
}
export type AlertType = 'OVERLOAD' | 'AVAILABLE_CAPACITY' | 'WORKLOAD_DRIFT' | 'LATE_RISK';
export type AlertStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED';
export interface Alert {
  alertId: number;
  projectId: number;
  userId: number | null;
  taskId: number | null;
  alertType: AlertType;
  severity: Severity;
  reason: string;
  ruleVersion: string;
  status: AlertStatus;
  createdAt: Instant;
  acknowledgedBy: number | null;
  acknowledgedAt: Instant | null;
  resolvedAt: Instant | null;
}
export type LateRiskStatus =
  | 'NO_RISK'
  | 'NO_IMMEDIATE_RISK'
  | 'OVERDUE'
  | 'AT_RISK_UNRESOLVED_BLOCKER'
  | 'AT_RISK_INSUFFICIENT_CAPACITY'
  | 'DUE_SOON_ON_TRACK'
  | 'UNKNOWN';

// ---- Guest links (Ramanjot) ----
export type GuestLinkStatus = 'ACTIVE' | 'EXPIRED' | 'REVOKED';
export interface GuestLink {
  linkId: number;
  projectId: number;
  createdBy: number;
  createdAt: Instant;
  expiresAt: Instant | null;
  revokedAt: Instant | null;
  status: GuestLinkStatus;
}
export interface CreatedGuestLink {
  link: GuestLink;
  shareUrl: string;
}
export interface GuestProjectView {
  projectName: string;
  summary: string | null;
  overallProgressPercent: number | null;
  milestones: { name: string; progressPercent: number | null; deadlineStatus: DeadlineStatus }[];
  tasks: { title: string; status: TaskStatus }[];
  memberDisplayNames: string[];
}

// ---- Notifications (Raegen) ----
export interface Notification {
  notifId: number;
  userId: number;
  notifType: string;
  projectId: number | null;
  taskId: number | null;
  message: string;
  isRead: boolean;
  readAt: Instant | null;
  emailSentAt: Instant | null;
  createdAt: Instant;
}
export interface NotificationPreference {
  userId: number;
  emailEnabled: boolean;
  inAppEnabled: boolean;
  earlyNotifTime: string | null;
}
