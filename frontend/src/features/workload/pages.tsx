// Workload and alerts UI. Owner: Liv.
import { TodoPage } from '../../components/TodoPage';

/** Embedded in the dashboard for leads. */
export function WorkloadPanel({ projectId }: { projectId: number }) {
  return (
    <TodoPage
      title={`Workload (project ${projectId})`}
      owner="Liv"
      api={['workloadApi.get']}
      todos={['Assigned vs expected share per member with OVERLOAD / AVAILABLE_CAPACITY flags', 'Handle insufficientData']}
    />
  );
}

export function AvailabilityPage() {
  return (
    <TodoPage title="My availability" owner="Liv" api={['workloadApi.reportAvailability']} todos={['Hours per week (0-80) and "I am overloaded" toggle']} />
  );
}

export function AlertsPage() {
  return (
    <TodoPage
      title="Workload alerts"
      owner="Liv"
      api={['workloadApi.alerts', 'workloadApi.updateAlert']}
      todos={['Alerts with reason, severity, rule version; filter by status', 'Acknowledge / resolve']}
    />
  );
}
