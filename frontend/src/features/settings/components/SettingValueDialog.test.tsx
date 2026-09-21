import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { ConfirmDialogProvider } from '@/components/ConfirmDialog'
import { ApiError } from '@/types/api'

import { SettingValueDialog } from './SettingValueDialog'
import type { SystemSetting } from '../types'

const update = vi.hoisted(() => vi.fn())

vi.mock('../api/settingsApi', () => ({ settingsApi: { update } }))

const FILE_CAP: SystemSetting = {
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
}

const VISIBILITY: SystemSetting = {
  key: 'profiles.visibility_window_days',
  category: 'Business',
  valueType: 'Integer',
  value: 14,
  defaultValue: 14,
  minValue: 1,
  maxValue: 3650,
  overridden: false,
  retroactive: true,
  updatedByName: null,
  updatedAt: null,
}

function renderDialog(setting: SystemSetting) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  function wrap(children: ReactNode) {
    // The confirm provider is real and not a stub: FormDialog asks it before discarding a dirty
    // form (#231), so a dialog rendered without it throws rather than rendering.
    return (
      <QueryClientProvider client={queryClient}>
        <ConfirmDialogProvider>{children}</ConfirmDialogProvider>
      </QueryClientProvider>
    )
  }
  return render(wrap(<SettingValueDialog setting={setting} open onOpenChange={vi.fn()} />))
}

/** The one field every case has to fill before the form will submit at all. */
async function fillReason(text = 'porque sí') {
  await userEvent.type(screen.getByLabelText('Motivo'), text)
}

beforeEach(() => {
  update.mockReset()
  update.mockResolvedValue(FILE_CAP)
})

describe('SettingValueDialog', () => {
  /**
   * The field opens on the value in force, not on a blank. Most changes are a nudge from the current
   * number rather than a number typed from nothing, and a blank box hides what is being replaced.
   */
  it('opens on the value that is currently in force', () => {
    renderDialog(FILE_CAP)

    expect(screen.getByLabelText(/Nuevo valor/)).toHaveValue('2')
  })

  /**
   * The range and the default, said before the value is saved. A cap somebody only discovers by
   * hitting it reads as a bug rather than a rule (principio 2 de frontend-diseno.md §1), and every
   * setting here has a range of its own — so a shared "must be a number" would be exactly that bug.
   */
  it('states the range and the default before anything is typed', () => {
    renderDialog(FILE_CAP)

    expect(screen.getByText(/Entre 1 y 25/)).toBeInTheDocument()
    expect(screen.getByText(/Por defecto: 2/)).toBeInTheDocument()
  })

  /** And the unit, because "5" without it does not say five of what. */
  it('names the unit the value is measured in', () => {
    renderDialog(FILE_CAP)

    expect(screen.getByLabelText('Nuevo valor (MB)')).toBeInTheDocument()
  })

  /**
   * **The warning of #141, before the value is saved and not after.** The visibility window is
   * evaluated on every profile read, so lowering it takes visibility away from people who have it at
   * this instant — #141 says in so many words that «ninguno de los dos es un ajuste cosmético y la
   * pantalla tiene que decirlo».
   */
  it('warns before saving a setting whose change is retroactive', () => {
    renderDialog(VISIBILITY)

    expect(screen.getByText(/este cambio no espera/i)).toBeInTheDocument()
  })

  /** And says nothing of the sort for the three that are not, so the warning keeps meaning something. */
  it('does not warn on a setting whose change is not retroactive', () => {
    renderDialog(FILE_CAP)

    expect(screen.queryByText(/este cambio no espera/i)).not.toBeInTheDocument()
  })

  /**
   * Out of range is refused here, in the reader's language and naming both bounds. The server refuses
   * it too with `SETTING_OUT_OF_RANGE` (#197) — this is what makes the refusal immediate rather than
   * what makes it safe.
   */
  it('refuses a value outside the setting’s own range, naming both bounds', async () => {
    renderDialog(FILE_CAP)

    await userEvent.clear(screen.getByLabelText(/Nuevo valor/))
    await userEvent.type(screen.getByLabelText(/Nuevo valor/), '500')
    await fillReason()
    await userEvent.click(screen.getByRole('button', { name: 'Guardar el cambio' }))

    expect(await screen.findByText('Tiene que estar entre 1 y 25')).toBeInTheDocument()
    expect(update).not.toHaveBeenCalled()
  })

  /** A value that is not a whole number is refused too, and never silently rounded. */
  it('refuses anything that is not a whole number', async () => {
    renderDialog(FILE_CAP)

    await userEvent.clear(screen.getByLabelText(/Nuevo valor/))
    await userEvent.type(screen.getByLabelText(/Nuevo valor/), '2,5')
    await fillReason()
    await userEvent.click(screen.getByRole('button', { name: 'Guardar el cambio' }))

    expect(await screen.findByText('Escribí un número entero, sin comas ni puntos')).toBeInTheDocument()
    expect(update).not.toHaveBeenCalled()
  })

  /**
   * The reason is mandatory, and this is the one thing that makes a settings change reviewable: it
   * sends no notification and appears nowhere else (#141).
   */
  it('does not submit without a reason', async () => {
    renderDialog(FILE_CAP)

    await userEvent.clear(screen.getByLabelText(/Nuevo valor/))
    await userEvent.type(screen.getByLabelText(/Nuevo valor/), '5')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar el cambio' }))

    await waitFor(() => expect(update).not.toHaveBeenCalled())
  })

  /** The happy path, and the shape the API is actually given: a number, not the typed string. */
  it('sends the new value as a number, with its reason', async () => {
    renderDialog(FILE_CAP)

    await userEvent.clear(screen.getByLabelText(/Nuevo valor/))
    await userEvent.type(screen.getByLabelText(/Nuevo valor/), '5')
    await fillReason('los mapas pesan más de lo que pensábamos')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar el cambio' }))

    await waitFor(() =>
      expect(update).toHaveBeenCalledWith('files.max_file_size_mb', {
        value: 5,
        justification: 'los mapas pesan más de lo que pensábamos',
      }),
    )
  })

  /**
   * A refusal from the server is rendered inline, beside the field, with its numbers interpolated —
   * not as a toast that vanishes while the form is still open, and never as the backend's English
   * `detail` (#197).
   */
  it('renders the server’s refusal inline, with the bounds it sent', async () => {
    update.mockRejectedValue(
      new ApiError(400, {
        title: 'Bad Request',
        status: 400,
        detail: 'Setting files.max_file_size_mb must be between 1 and 25, got 500',
        errorCode: 'SETTING_OUT_OF_RANGE',
        errorParams: { minValue: '1', maxValue: '25' },
      }),
    )
    renderDialog(FILE_CAP)

    await userEvent.clear(screen.getByLabelText(/Nuevo valor/))
    await userEvent.type(screen.getByLabelText(/Nuevo valor/), '5')
    await fillReason()
    await userEvent.click(screen.getByRole('button', { name: 'Guardar el cambio' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('tiene que estar entre 1 y 25')
    expect(alert).not.toHaveTextContent('must be between')
  })
})
