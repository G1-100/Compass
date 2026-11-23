/**
 * Shared UI building blocks every page should use so behavior is consistent (Detailed Design 3.5 conventions).
 * Owner: Adam (frontend lead). Signatures are fixed; TODO bodies are marked.
 */
import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { ApiError } from '../api/client';

/** Shows the server's message for one field from ApiError.fields. */
export function FieldError({ error, field }: { error: unknown; field: string }) {
  if (!(error instanceof ApiError) || !error.fields[field]) return null;
  return <span className="field-error">{error.fields[field]}</span>;
}

/**
 * Page-level error banner. For TIMEOUT/NETWORK shows "Connection problem, try again" with a Retry button.
 * Form inputs must keep their values (keep form state in the page, not in this component).
 */
export function ErrorBanner({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  if (!error) return null;
  const message = error instanceof ApiError ? error.message : 'Something went wrong. Please try again.';
  const retryable = error instanceof ApiError && (error.code === 'TIMEOUT' || error.code === 'NETWORK');
  return (
    <div className="error-banner" role="alert">
      {message}
      {retryable && onRetry && (
        <button type="button" onClick={onRetry}>
          Retry
        </button>
      )}
    </div>
  );
}

/** Submit button that is disabled while a request is in flight, preventing double submits. */
export function SubmitButton({ pending, children, ...rest }: { pending: boolean; children: ReactNode } & ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button type="submit" disabled={pending || rest.disabled} {...rest}>
      {pending ? 'Saving…' : children}
    </button>
  );
}

/**
 * Shown on 409 VERSION_CONFLICT. Displays the latest saved record (error.current) and keeps the user's own
 * edits in a "copy my changes" area. "Reload latest" replaces the form with the server version.
 * Feature-specific saved values can be shown above the copyable draft.
 */
export function ConflictDialog<T>({
  latest,
  latestContent,
  myChanges,
  onReloadLatest,
  onClose,
}: {
  latest: T;
  latestContent?: ReactNode;
  myChanges: string;
  onReloadLatest: (latest: T) => void;
  onClose: () => void;
}) {
  return (
    <div role="dialog" aria-label="Version conflict" className="dialog">
      <h2>Version conflict</h2>
      <p>Someone else saved changes. Close to keep editing your draft, or reload the latest saved version.</p>
      {latestContent}
      <label>
        Your unsaved changes
        <textarea readOnly value={myChanges} />
      </label>
      <button type="button" onClick={() => onReloadLatest(latest)}>
        Reload latest
      </button>
      <button type="button" onClick={onClose}>
        Close
      </button>
    </div>
  );
}
