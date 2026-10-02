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
          // Every table of theirs that stopped being active (#133): `my-tables` above now answers
          // only for the live ones, and this is where the rest went.
          { path: 'history', lazy: () => import('./player/PlayerHistoryPage') },
          // The two profile screens of F2.3 (#248): the reader's own, and anybody else's, subject
          // to the visibility rules of #41, #44 and #47.
          { path: 'profile', lazy: () => import('./player/ProfilePage') },
          { path: 'users/:id', lazy: () => import('./player/UserProfilePage') },
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
          // The support screen `/help` was reserved for (#231): **not** the old help route coming
          // back - the explanations are still dialogs raised from the screen that prompts the
          // question. This is the "ask for assistance" half, which had no backend until the
          // `General` request of F3.2 gave it one (#42).
          { path: 'help', lazy: () => import('./SupportPage') },
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
          // The home of the context (#270): a welcome and nothing else yet - there are no metrics
          // worth a dashboard. Every admin screen sits behind AdminLayout's guard: an account
          // without Admin or Owner is sent home before any of them paints (#269).
          { index: true, lazy: () => import('./admin/AdminHomePage') },
          // The shared tray (#100, F3.3): everything waiting on an admin, whichever table it lives
          // in, reserved one at a time.
          { path: 'queue', lazy: () => import('./admin/AdminQueuePage') },
          { path: 'tables', lazy: () => import('./admin/AdminTablesPage') },
          // One table, whole (#284): the master's own tab modules, mounted read-only - the admin has no
          // visibility limits (#45) - with Details as the first tab, since the admin did not write it.
          { path: 'tables/:id/edit', lazy: () => import('./admin/AdminTableEditPage') },
          {
            path: 'tables/:id',
            lazy: () => import('./admin/AdminTableDetailPage'),
            children: [
              { index: true, lazy: () => import('./admin/AdminTableDetailsTab') },
              { path: 'candidates', lazy: () => import('./master/MasterTableCandidatesTab') },
              { path: 'players', lazy: () => import('./master/MasterTablePlayersTab') },
              { path: 'schedule', lazy: () => import('./master/MasterTableScheduleTab') },
              { path: 'sessions', lazy: () => import('./master/MasterTableSessionsTab') },
              { path: 'tasks', lazy: () => import('./master/MasterTableTasksTab') },
              { path: 'files', lazy: () => import('./master/MasterTableFilesTab') },
              { path: 'history', lazy: () => import('./admin/AdminTableHistoryTab') },
            ],
          },
          { path: 'catalogs', lazy: () => import('./admin/AdminCatalogsPage') },
          // One group as a canvas of nodes (#275). Its own route and its own chunk, so React Flow
          // only loads for an admin who opens a group.
          { path: 'catalogs/:kind/:id', lazy: () => import('./admin/AdminCatalogGroupPage') },
          { path: 'files', lazy: () => import('./admin/AdminFilesPage') },
          // Uploading into the library is its own page, not a panel over the list (#278).
          { path: 'files/upload', lazy: () => import('./admin/AdminFileUploadPage') },
          // Accounts, their roles and their blocks (F3.1).
          { path: 'users', lazy: () => import('./admin/AdminUsersPage') },
          // One account, whole (#284): opened from the list and from any person on a table.
          { path: 'users/:id', lazy: () => import('./admin/AdminUserDetailPage') },
          // Every request somebody made of an admin (#42, F3.2).
          { path: 'requests', lazy: () => import('./admin/AdminRequestsPage') },
          // The editable configuration of #141 (F3.5).
          { path: 'settings', lazy: () => import('./admin/AdminSettingsPage') },
        ],
      },
      { path: '*', lazy: () => import('./NotFoundPage') },
    ],
  },
])
