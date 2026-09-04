import { createBrowserRouter } from 'react-router'

import { AdminLayout } from '@/layouts/AdminLayout'
import { MasterLayout } from '@/layouts/MasterLayout'
import { PlayerLayout } from '@/layouts/PlayerLayout'
import { PublicLayout } from '@/layouts/PublicLayout'
import { RootLayout } from '@/layouts/RootLayout'

/**
 * The tree mirrors the sitemap of frontend-diseno.md 2 - E1 registers only its subset of the 28
 * routes; the later stages add the rest here, and nowhere else.
 */
export const router = createBrowserRouter([
  {
    Component: RootLayout,
    children: [
      // /login builds its own frame: it is the one full-bleed screen, over the brand gradient
      // (#132). The other two public screens share PublicLayout's centred card.
      { path: '/login', lazy: () => import('./LoginPage') },
      {
        Component: PublicLayout,
        children: [
          { path: '/auth/callback', lazy: () => import('./OAuthCallbackPage') },
          { path: '/onboarding', lazy: () => import('./OnboardingPage') },
        ],
      },
      {
        Component: PlayerLayout,
        children: [
          { index: true, lazy: () => import('./TableListPage') },
          { path: 'tables/:id', lazy: () => import('./TableDetailPage') },
          { path: 'my/applications', lazy: () => import('./my/MyApplicationsPage') },
          { path: 'my/tables', lazy: () => import('./my/MyTablesPage') },
          { path: 'my/tables/:id', lazy: () => import('./my/MyTableDetailPage') },
          { path: 'notifications', lazy: () => import('./NotificationsPage') },
          // Global rather than part of the Player context: the three layouts are the same shell and
          // the header already adapts on its own. It sits here for the same reason /notifications
          // does. The audiences are child routes (#168): each with its own URL, linkable by #ref.
          {
            path: 'help',
            lazy: () => import('./help/HelpPage'),
            children: [
              { index: true, lazy: () => import('./help/HelpBasicsTab') },
              { path: 'players', lazy: () => import('./help/HelpPlayersTab') },
              { path: 'masters', lazy: () => import('./help/HelpMastersTab') },
              { path: 'admins', lazy: () => import('./help/HelpAdminsTab') },
            ],
          },
        ],
      },
      {
        path: 'master',
        Component: MasterLayout,
        children: [
          { index: true, lazy: () => import('./master/MasterDashboardPage') },
          { path: 'tables', lazy: () => import('./master/MasterTablesPage') },
          { path: 'tables/new', lazy: () => import('./master/MasterTableCreatePage') },
          // A sibling of the detail rather than one of its tabs, like `tables/new` is a sibling of
          // the list: rewriting the whole table is its own screen and does not want the tab chrome.
          { path: 'tables/:id/edit', lazy: () => import('./master/MasterTableEditPage') },
          {
            path: 'tables/:id',
            lazy: () => import('./master/MasterTableDetailPage'),
            children: [
              { index: true, lazy: () => import('./master/MasterTableCandidatesTab') },
              { path: 'players', lazy: () => import('./master/MasterTablePlayersTab') },
              { path: 'schedule', lazy: () => import('./master/MasterTableScheduleTab') },
              { path: 'sessions', lazy: () => import('./master/MasterTableSessionsTab') },
              { path: 'tasks', lazy: () => import('./master/MasterTableTasksTab') },
              { path: 'files', lazy: () => import('./master/MasterTableFilesTab') },
              { path: 'status', lazy: () => import('./master/MasterTableStatusTab') },
            ],
          },
        ],
      },
      {
        path: 'admin',
        Component: AdminLayout,
        children: [
          { path: 'tables', lazy: () => import('./admin/AdminTablesPage') },
          { path: 'catalogs', lazy: () => import('./admin/AdminCatalogsPage') },
          { path: 'files', lazy: () => import('./admin/AdminFilesPage') },
        ],
      },
      { path: '*', lazy: () => import('./NotFoundPage') },
    ],
  },
])
