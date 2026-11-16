/**
 * All routes. PLATFORM — complete. Add a route here when you add a page (one line, keeps conflicts small).
 */
import { BrowserRouter, Navigate, Route, Routes, useParams } from 'react-router';
import { AuthProvider } from './auth/AuthContext';
import { RequireAuth } from './auth/RequireAuth';
import { Layout } from './components/Layout';
import { ActivatePage, AdminUsersPage, PasswordResetPage, ProfilePage, RegisterPage, SignInPage } from './features/auth/pages';
import { CheckinDetailPage, CheckinsPage } from './features/checkins/pages';
import { GuestViewPage } from './features/guest/pages';
import { NotificationPreferencesPage, NotificationsPage } from './features/notifications/pages';
import { MilestonesPage, SprintsPage } from './features/sprints/pages';
import { MyTasksPage, TaskBoardPage, TaskDetailPage } from './features/tasks/pages';
import { DashboardPage, MyInvitationsPage, TeamMembersPage, TeamPage, TeamsPage } from './features/teams/pages';
import { AlertsPage, AvailabilityPage } from './features/workload/pages';

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          {/* Public */}
          <Route path="/sign-in" element={<SignInPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/activate" element={<ActivatePage />} />
          <Route path="/reset-password" element={<PasswordResetPage />} />
          <Route path="/guest/:token" element={<GuestViewPage />} />

          {/* Signed in */}
          <Route
            element={
              <RequireAuth>
                <Layout />
              </RequireAuth>
            }
          >
            <Route index element={<Navigate to="/teams" replace />} />
            <Route path="/profile" element={<ProfilePage />} />
            <Route path="/admin/users" element={<AdminUsersPage />} />
            <Route path="/teams" element={<TeamsPage />} />
            <Route path="/teams/:teamId" element={<TeamPage />} />
            <Route path="/teams/:teamId/members" element={<TeamMembersPage />} />
            <Route path="/teams/:teamId/checkins" element={<CheckinsPage />} />
            <Route path="/checkins/:checkinId" element={<CheckinDetailPage />} />
            <Route path="/invitations" element={<MyInvitationsPage />} />
            <Route path="/projects/:projectId" element={<DashboardPage />} />
            <Route path="/projects/:projectId/tasks" element={<TaskBoardPage />} />
            <Route path="/projects/:projectId/sprints" element={<SprintsPage />} />
            <Route path="/projects/:projectId/milestones" element={<MilestonesPage />} />
            <Route path="/projects/:projectId/alerts" element={<AlertsPage />} />
            <Route path="/projects/:projectId/availability" element={<AvailabilityPage />} />
            <Route path="/tasks/:taskId" element={<TaskDetailPage />} />
            <Route path="/my-tasks" element={<MyTasksPage />} />
            <Route path="/notifications" element={<NotificationsPage />} />
            <Route path="/settings/notifications" element={<NotificationPreferencesPage />} />
          </Route>

          <Route path="*" element={<NotFound />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}

function NotFound() {
  return <p>Page not found.</p>;
}

/** Helper for pages: const projectId = useNumberParam('projectId'); */
export function useNumberParam(name: string): number {
  const params = useParams();
  return Number(params[name]);
}
