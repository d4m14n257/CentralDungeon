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
          { path: 'notifications', lazy: () => import('./NotificationsPage') },
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
              { path: 'status', lazy: () => import('./master/MasterTableStatusTab') },
            ],
          },
        ],
      },
      {
        path: 'admin',
        Component: AdminLayout,
        children: [{ path: 'tables', lazy: () => import('./admin/AdminTablesPage') }],
      },
      { path: '*', lazy: () => import('./NotFoundPage') },
    ],
  },
])
