import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { ConfirmDialogProvider } from '@/components/ConfirmDialog'
import type { SystemSetting } from '@/features/settings'
import { ApiError } from '@/types/api'

import { AdminSettingsPage } from './AdminSettingsPage'

const list = vi.hoisted(() => vi.fn())
const history = vi.hoisted(() => vi.fn())
const update = vi.hoisted(() => vi.fn())

// The API module is mocked, not the hooks: that keeps the query keys, the grouping and the four
// states running for real, which is where this screen's behaviour actually lives.
vi.mock('@/features/settings/api/settingsApi', () => ({ settingsApi: { list, history, update } }))

function setting(overrides: Partial<SystemSetting> = {}): SystemSetting {
  return {
    key: 'files.max_file_size_mb',
    category: 'Limits',
    valueType: 'Integer',
    value: 2,
    defaultValue: 2,
    minValue: 1,
    maxValue: 25,
    overridden: false,
    retroactive: false,
    updatedByName: null,
    updatedAt: null,
    ...overrides,
  }
}

const VISIBILITY = setting({
  key: 'profiles.visibility_window_days',
  category: 'Business',
  value: 14,
  defaultValue: 14,
  minValue: 1,
  maxValue: 3650,
  retroactive: true,
})

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  function wrap(children: ReactNode) {
    return (
      <MemoryRouter>
        <QueryClientProvider client={queryClient}>
          <ConfirmDialogProvider>{children}</ConfirmDialogProvider>
        </QueryClientProvider>
      </MemoryRouter>
    )
  }
  return render(wrap(<AdminSettingsPage />))
}

beforeEach(() => {
  list.mockReset()
  history.mockReset()
  update.mockReset()
  history.mockResolvedValue([])
})

describe('AdminSettingsPage', () => {
  /** Grouped by category, which is what keeps this from being a flat list of numbers (#141). */
  it('groups the settings by category', async () => {
    list.mockResolvedValue([setting(), VISIBILITY])
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Límites y cuotas' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Parámetros de negocio' })).toBeInTheDocument()
  })

  /**
   * **The default beside the value in force**, which is the thing the slice was asked for: without it
   * an admin looking at a number cannot tell whether somebody set it there, and cannot put it back
   * without finding it in the source.
   */
  it('shows the value in force beside what the setting ships as', async () => {
    list.mockResolvedValue([
      setting({ value: 5, defaultValue: 2, overridden: true, updatedByName: 'Ana', updatedAt: '2026-03-01T10:00:00' }),
    ])
    renderPage()

    expect(await screen.findByText('5 MB')).toBeInTheDocument()
    expect(screen.getByText('Por defecto: 2')).toBeInTheDocument()
  })

  /** And who set it, because an audited change nobody can see the author of is not audited. */
  it('names who last set an overridden value', async () => {
    list.mockResolvedValue([setting({ value: 5, overridden: true, updatedByName: 'Ana Valdez', updatedAt: '2026-03-01T10:00:00' })])
    renderPage()

    expect(await screen.findByText(/Ana Valdez/)).toBeInTheDocument()
  })

  /**
   * **`overridden`, never `value !== defaultValue`.** Somebody may set a value to exactly its default,
   * and that is a decision they made and signed — a screen that derived the flag would erase it.
   */
  it('says a setting is still on the shipped default only when nobody has set it', async () => {
    list.mockResolvedValue([setting()])
    const { unmount } = renderPage()
    expect(await screen.findByText(/Nunca se cambió/)).toBeInTheDocument()
    unmount()

    list.mockResolvedValue([setting({ overridden: true, updatedByName: 'Ana', updatedAt: '2026-03-01T10:00:00' })])
    renderPage()
    expect(await screen.findByText(/Lo cambió Ana/)).toBeInTheDocument()
    expect(screen.queryByText(/Nunca se cambió/)).not.toBeInTheDocument()
  })

  /**
   * The retroactive warning is on the card and not only inside the dialog: somebody scanning the
   * screen for something to adjust has to see which one is not cosmetic before deciding to open it
   * (#44, #141).
   */
  it('marks the retroactive setting on the card itself', async () => {
    list.mockResolvedValue([setting(), VISIBILITY])
    renderPage()

    expect(await screen.findByText(/este cambio no espera/i)).toBeInTheDocument()
  })

  /** Opening the editor carries that setting's own bounds into the form, not a shared range. */
  it('opens the editor on the setting whose button was pressed', async () => {
    list.mockResolvedValue([setting(), VISIBILITY])
    renderPage()

    // The card of the file cap and not the other one on screen: the point is that the dialog opens
    // on *this* setting's bounds, so it has to be reached from its own row.
    const card = (await screen.findByText('Tope por archivo')).closest('li') as HTMLElement
    await userEvent.click(within(card).getByRole('button', { name: 'Cambiar' }))

    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByLabelText('Nuevo valor (MB)')).toHaveValue('2')
    expect(within(dialog).getByText(/Entre 1 y 25/)).toBeInTheDocument()
    // And never the visibility window's, which is the mistake a shared schema would make.
    expect(within(dialog).queryByText(/Entre 1 y 3650/)).not.toBeInTheDocument()
  })

  /** The history panel is offered on every row: reading the record is not a change. */
  it('opens the history of the setting whose button was pressed', async () => {
    list.mockResolvedValue([setting()])
    renderPage()

    const card = (await screen.findByText('Tope por archivo')).closest('li') as HTMLElement
    await userEvent.click(within(card).getByRole('button', { name: 'Historial' }))

    expect(await screen.findByText('Historial de «Tope por archivo»')).toBeInTheDocument()
    expect(history).toHaveBeenCalledWith('files.max_file_size_mb')
  })

  // ------------------------------------------------------------- the four states

  it('shows a skeleton while the settings load', () => {
    list.mockReturnValue(new Promise(() => {}))
    const { container } = renderPage()

    expect(container.querySelector('[data-slot="skeleton"]')).toBeInTheDocument()
  })

  it('offers a retry when the settings cannot be read', async () => {
    list.mockRejectedValue(new Error('offline'))
    renderPage()

    expect(await screen.findByRole('button', { name: /Reintentar/i })).toBeInTheDocument()
  })

  /**
   * Only reachable if the backend ever published an empty catalogue, which would mean every setting
   * had been removed — a real thing to say rather than a blank page.
   */
  it('says something when the platform publishes no settings at all', async () => {
    list.mockResolvedValue([])
    renderPage()

    expect(await screen.findByText('No hay nada configurable')).toBeInTheDocument()
  })

  /**
   * No role guard in front of the route (#103): somebody who forces it without the rank gets the
   * backend's 403 and an explanation rather than a blank page.
   */
  it('explains the refusal when the reader holds neither rank', async () => {
    list.mockRejectedValue(new ApiError(403, { title: 'Forbidden', status: 403, detail: 'Access denied', errorCode: 'FORBIDDEN' }))
    renderPage()

    expect(await screen.findByText(/no tenés permiso/i)).toBeInTheDocument()
  })
})
