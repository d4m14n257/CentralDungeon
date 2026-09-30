import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'

import { AdminFileUploadPage } from './AdminFileUploadPage'

const uploadToLibrary = vi.hoisted(() => vi.fn())

// The API module is mocked, not the hook: the page's own rule — every file says what it is before
// anything is sent — is what these cases are about, and it runs for real.
vi.mock('@/features/files/api/filesApi', () => ({ filesApi: { uploadToLibrary } }))
// The dropzone asks the server for the size cap; the shipped default is enough here.
vi.mock('@/hooks/useClientLimits', () => ({
  useClientLimits: () => ({ maxFileSizeBytes: 2 * 1024 * 1024, claimTimeoutMinutes: 15 }),
}))

function renderPage() {
  const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <AdminFileUploadPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

const pdf = (name: string) => new File(['hoja'], name, { type: 'application/pdf' })

describe('AdminFileUploadPage', () => {
  /** Each dropped file gets its own select, so several can go up at once, each as what it is (#278). */
  it('gives every staged file its own select', async () => {
    renderPage()

    await userEvent.upload(document.querySelector('input[type="file"]') as HTMLInputElement, [pdf('ficha.pdf'), pdf('reglas.pdf')])

    expect(screen.getByRole('combobox', { name: 'Qué es «ficha.pdf»' })).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Qué es «reglas.pdf»' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Subir 2 archivos' })).toBeEnabled()
  })

  /**
   * Nothing lands in the library without saying which flow it is for (#278, M24.1): with a file still
   * unclassified, «Subir» sends nothing and says what is missing instead of greying out silently.
   */
  it('sends nothing and says so while a file has no cajón', async () => {
    renderPage()

    await userEvent.upload(document.querySelector('input[type="file"]') as HTMLInputElement, pdf('ficha.pdf'))
    await userEvent.click(screen.getByRole('button', { name: 'Subir 1 archivo' }))

    expect(screen.getByText('Falta elegir qué es 1 archivo.')).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Qué es «ficha.pdf»' })).toHaveAttribute('aria-invalid', 'true')
    expect(uploadToLibrary).not.toHaveBeenCalled()
  })
})
