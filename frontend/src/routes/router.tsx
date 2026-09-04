import { createBrowserRouter } from 'react-router'

import { AdminLayout } from '@/layouts/AdminLayout'
import { MasterLayout } from '@/layouts/MasterLayout'
import { PlayerLayout } from '@/layouts/PlayerLayout'
import { PublicLayout } from '@/layouts/PublicLayout'
import { RootLayout } from '@/layouts/RootLayout'

/**
 * El árbol espeja el sitemap de frontend-diseno.md 2 - E1 registra solo su subconjunto de las
 * 28 rutas totales; las etapas siguientes agregan el resto acá, no en otro lado.
 */
export const router = createBrowserRouter([
  {
    Component: RootLayout,
    children: [
      // /login arma su propio encuadre: es la única pantalla a sangre, sobre el gradiente de
      // marca (#132). Las otras dos públicas comparten la tarjeta centrada de PublicLayout.
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
          // Global, no del contexto Jugador: los tres layouts son el mismo shell y la cabecera
          // ya se adapta sola. Va acá por la misma razón que /notifications. Las audiencias son
          // rutas hijas (#168): cada una con URL propia, enlazable con su #ref.
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
          { path: 'tables', lazy: () => import('./master/MasterTablesPage') },
          { path: 'tables/new', lazy: () => import('./master/MasterTableCreatePage') },
          {
            path: 'tables/:id',
            lazy: () => import('./master/MasterTableDetailPage'),
            children: [
              { index: true, lazy: () => import('./master/MasterTableCandidatesTab') },
              { path: 'sessions', lazy: () => import('./master/MasterTableSessionsTab') },
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
