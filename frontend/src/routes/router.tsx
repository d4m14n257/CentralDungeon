import { createBrowserRouter } from 'react-router'

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
      {
        Component: PublicLayout,
        children: [
          { path: '/login', lazy: () => import('./LoginPage') },
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
          {
            path: 'tables/:id',
            lazy: () => import('./master/MasterTableDetailPage'),
            children: [{ index: true, lazy: () => import('./master/MasterTableCandidatesTab') }],
          },
        ],
      },
      { path: '*', lazy: () => import('./NotFoundPage') },
    ],
  },
])
