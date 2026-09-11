import { createBrowserRouter } from 'react-router'

import { AdminLayout } from '@/layouts/AdminLayout'
import { MasterLayout } from '@/layouts/MasterLayout'
import { PlayerLayout } from '@/layouts/PlayerLayout'
import { PublicLayout } from '@/layouts/PublicLayout'
import { RootLayout } from '@/layouts/RootLayout'
import { ShellLayout } from '@/layouts/ShellLayout'

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
      // `/` is not a screen: every context owns a prefix, so the root only dispatches to the home
      // of the one the reader has (#222).
      { index: true, lazy: () => import('./RootRedirect') },
      {
        path: 'player',
        Component: PlayerLayout,
        children: [
          { index: true, lazy: () => import('./player/TableListPage') },
          { path: 'tables/:id', lazy: () => import('./player/TableDetailPage') },
          { path: 'applications', lazy: () => import('./player/MyApplicationsPage') },
          { path: 'my-tables', lazy: () => import('./player/MyTablesPage') },
          { path: 'my-tables/:id', lazy: () => import('./player/MyTableDetailPage') },
        ],
      },
      // The two transversal screens: they belong to no context and get the bare shell, without any
      // section nav. Which is why they keep the chip on whatever context the reader came from
      // (#222) instead of flipping it.
      {
        Component: ShellLayout,
        children: [
          { path: 'notifications', lazy: () => import('./NotificationsPage') },
          // The reader's own week (#227). Transversal like the two above, and for the same reason:
          // the evenings somebody runs and the evenings they play are the same evenings.
          { path: 'my/schedule', lazy: () => import('./my/MySchedulePage') },
          // The reader's own library (#65, #232). Transversal for the same reason: the sheet you
          // applied with and the map you attached to a table you run are one library, not two.
          { path: 'my/files', lazy: () => import('./my/MyFilesPage') },
          // `/help` is not a route any more (#231): the explanations are dialogs raised from the
          // screen that prompts the question. The path stays unclaimed for the support screen -
          // asking for assistance, reporting a bug - which has no backend yet.
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
