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
