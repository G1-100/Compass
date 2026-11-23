// Small task UI pieces shared by the board, My tasks and the task panel. Owner: Adam.
import type { ReactNode } from 'react';
import type { Assignee, LateRiskStatus, Task, TaskStatus } from '../../api/types';
import { formatDate, initials, riskBadge, STATUS_LABEL, STATUS_ORDER } from './data';
import styles from './Tasks.module.css';

const STATUS_CLASS: Record<TaskStatus, string> = {
  NOT_STARTED: styles.statusNotStarted,
  IN_PROGRESS: styles.statusInProgress,
  BLOCKED: styles.statusBlocked,
  DONE: styles.statusDone,
};

/** Status pill. When `onChange` is given it becomes a dropdown (only assignees may change status). */
export function StatusPill({
  status,
  onChange,
  disabled,
  label,
}: {
  status: TaskStatus;
  onChange?: (status: TaskStatus) => void;
  disabled?: boolean;
  label?: string;
}) {
  const className = `${styles.pill} ${STATUS_CLASS[status]}`;
  if (!onChange) return <span className={className}>{STATUS_LABEL[status]}</span>;
  return (
    <span className={`${className} ${styles.pillSelect}`} onClick={(event) => event.stopPropagation()}>
      <select
        value={status}
        disabled={disabled}
        aria-label={label ?? 'Change status'}
        onChange={(event) => onChange(event.target.value as TaskStatus)}
      >
        {STATUS_ORDER.map((value) => (
          <option key={value} value={value}>
            {STATUS_LABEL[value]}
          </option>
        ))}
      </select>
      <span aria-hidden="true">{STATUS_LABEL[status]}</span>
      <Caret />
    </span>
  );
}

export function RiskPill({ risk }: { risk: LateRiskStatus }) {
  const badge = riskBadge(risk);
  if (!badge) return <span className={styles.dash}>—</span>;
  return (
    <span className={`${styles.pill} ${styles[`risk_${badge.tone}`]}`}>
      {badge.icon && <WarningIcon />}
      {badge.label}
    </span>
  );
}

const AVATAR_TONES = [styles.toneGreen, styles.toneBlue, styles.toneOrange, styles.tonePink, styles.toneYellow];

/** Stable bright-palette colour per person. */
function toneFor(name: string): string {
  let hash = 0;
  for (const ch of name) hash = (hash * 31 + ch.charCodeAt(0)) | 0;
  return AVATAR_TONES[Math.abs(hash) % AVATAR_TONES.length];
}

export function Avatar({ name, size = 'sm' }: { name: string; size?: 'sm' | 'md' }) {
  return (
    <span className={`${styles.avatar} ${toneFor(name)} ${size === 'md' ? styles.avatarMd : ''}`} title={name} aria-label={name}>
      {initials(name)}
    </span>
  );
}

export function AvatarStack({ assignees }: { assignees: Assignee[] }) {
  if (assignees.length === 0) return <span className={styles.dash}>—</span>;
  const shown = assignees.slice(0, 3);
  return (
    <span className={styles.avatarStack}>
      {shown.map((a) => (
        <Avatar key={a.userId} name={a.displayName} />
      ))}
      {assignees.length > shown.length && <span className={styles.avatarMore}>+{assignees.length - shown.length}</span>}
    </span>
  );
}

export function Caret() {
  return (
    <svg className={styles.caret} viewBox="0 0 10 10" aria-hidden="true">
      <path d="M2 3.5h6L5 7z" />
    </svg>
  );
}

export function WarningIcon() {
  return (
    <svg className={styles.warnIcon} viewBox="0 0 16 16" aria-hidden="true">
      <path d="M8 1.5 15 14H1z" />
      <path d="M8 6v3.6M8 11.4v.1" className={styles.warnMark} />
    </svg>
  );
}

export function SearchIcon() {
  return (
    <svg className={styles.searchIcon} viewBox="0 0 20 20" aria-hidden="true">
      <circle cx="8.5" cy="8.5" r="5.5" />
      <path d="m13 13 4 4" />
    </svg>
  );
}

/** Renders comment text with @mentions highlighted. */
export function MentionText({ text }: { text: string }) {
  const parts = text.split(/(@[\w.-]+)/g);
  return (
    <>
      {parts.map((part, i) =>
        part.startsWith('@') ? (
          <span key={i} className={styles.mention}>
            {part}
          </span>
        ) : (
          part
        ),
      )}
    </>
  );
}

const STATUS_ICON_CLASS: Record<TaskStatus, string> = {
  NOT_STARTED: styles.iconNotStarted,
  IN_PROGRESS: styles.iconInProgress,
  BLOCKED: styles.iconBlocked,
  DONE: styles.iconDone,
};

/** Round leading icon for a task row, like the circular icons in an activity feed. */
export function StatusIcon({ status }: { status: TaskStatus }) {
  return (
    <span className={`${styles.statusIcon} ${STATUS_ICON_CLASS[status]}`} aria-hidden="true">
      <svg viewBox="0 0 24 24">
        {status === 'DONE' && <path d="m6.5 12.5 3.5 3.5 7.5-8" className={styles.iconStroke} />}
        {status === 'IN_PROGRESS' && (
          <>
            <circle cx="12" cy="12" r="6.5" className={styles.iconStroke} />
            <path d="M12 5.5a6.5 6.5 0 0 1 0 13z" />
          </>
        )}
        {status === 'BLOCKED' && <path d="M12 7v6.5M12 17v.01" className={styles.iconStroke} />}
        {status === 'NOT_STARTED' && <circle cx="12" cy="12" r="6.5" className={styles.iconStrokeDashed} />}
      </svg>
    </span>
  );
}

/** One task in the activity-style list: icon, title + meta line, people, status, due/risk. */
export function TaskRow({
  task,
  active,
  meta,
  status,
  trailing,
  onOpen,
}: {
  task: Task;
  active: boolean;
  meta: string;
  status: ReactNode;
  trailing?: ReactNode;
  onOpen: () => void;
}) {
  return (
    <li
      className={`${styles.row} ${active ? styles.rowActive : ''}`}
      tabIndex={0}
      onClick={onOpen}
      onKeyDown={(event) => {
        if (event.key === 'Enter' && event.target === event.currentTarget) onOpen();
      }}
      aria-current={active ? 'true' : undefined}
    >
      <StatusIcon status={task.status} />
      <div className={styles.rowMain}>
        <span className={styles.rowTitle}>{task.title}</span>
        <span className={styles.rowMeta}>{meta}</span>
      </div>
      <div className={styles.rowPeople}>
        <AvatarStack assignees={task.assignees} />
      </div>
      <div className={styles.rowStatus}>{status}</div>
      <div className={styles.rowEnd}>
        {trailing ?? (
          <>
            <span className={`${styles.rowDue} ${task.overdue ? styles.overdueText : ''}`}>
              {task.dueDate ? formatDate(task.dueDate) : 'No date'}
            </span>
            {riskBadge(task.lateRisk) ? (
              <RiskPill risk={task.lateRisk} />
            ) : (
              <span className={styles.rowCaption}>{task.status === 'DONE' ? 'Completed' : 'On track'}</span>
            )}
          </>
        )}
      </div>
    </li>
  );
}
