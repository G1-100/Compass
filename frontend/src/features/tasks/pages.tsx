// Task board, task detail, and my tasks. Owner: Adam.
import { TodoPage } from '../../components/TodoPage';

export function TaskBoardPage() {
  return (
    <TodoPage
      title="Tasks"
      owner="Adam"
      api={['tasksApi.list', 'tasksApi.create', 'tasksApi.suggest', 'tasksApi.updateStatus', 'tasksApi.approve', 'tasksApi.delete']}
      todos={[
        'Board/list with filters (status, assignee, sprint, backlog, overdue, search) and sorting',
        'Overdue and late-risk badges on each task',
        'Leads: create task, approve/reject suggestions. Members: suggest a task',
        'Assignees change status; disable the control for others',
        'Open a task in <TaskDetailModal>',
      ]}
    />
  );
}

export function TaskDetailPage() {
  return (
    <TodoPage
      title="Task"
      owner="Adam"
      api={['tasksApi.get', 'tasksApi.update', 'tasksApi.replaceAssignees', 'tasksApi.comments', 'tasksApi.addComment', 'tasksApi.activity']}
      todos={[
        'Details, assignees (with optional % split), milestone, blockers',
        'Edit form with Cancel that sends nothing; ConflictDialog on 409',
        'Comments with @mentions; activity modal',
      ]}
    />
  );
}

export function MyTasksPage() {
  return <TodoPage title="My tasks" owner="Adam" api={['tasksApi.listMine']} todos={['My assigned tasks across all teams, same filters and sorting']} />;
}
