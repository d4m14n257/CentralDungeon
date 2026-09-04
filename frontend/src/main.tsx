// The @theme's two families, self-hosted. Without this the browser falls back to the system's
// ui-serif, and the application only looks like the design system on a machine that already has
// the fonts installed.
import '@fontsource-variable/inter'
import '@fontsource/spectral/500.css'
import '@fontsource/spectral/600.css'
import '@fontsource/spectral/700.css'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router'

import { ConfirmDialogProvider } from '@/components/ConfirmDialog'
import { Toaster } from '@/components/ui/sonner'
import { AuthProvider } from '@/providers/AuthProvider'
import { I18nProvider } from '@/providers/I18nProvider'
import { QueryProvider } from '@/providers/QueryProvider'
import { ThemeProvider } from '@/providers/ThemeProvider'
import { router } from '@/routes/router'
import '@/styles/globals.css'
import '@/styles/base.css'

const root = document.getElementById('root')
if (!root) throw new Error('#root is missing from index.html')

createRoot(root).render(
  <StrictMode>
    <I18nProvider>
      <ThemeProvider>
        <QueryProvider>
          <AuthProvider>
            <ConfirmDialogProvider>
              <RouterProvider router={router} />
              <Toaster />
            </ConfirmDialogProvider>
          </AuthProvider>
        </QueryProvider>
      </ThemeProvider>
    </I18nProvider>
  </StrictMode>,
)
