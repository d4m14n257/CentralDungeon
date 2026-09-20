import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { ConfirmDialogProvider } from '@/components/ConfirmDialog'
import type { StagedFile } from '@/types/file'
import { ApplyToTableDialog, type ApplyToTableDialogProps } from './ApplyToTableDialog'

const mutate = vi.fn((_input: unknown, options?: { onSuccess?: () => void }) => {
  options?.onSuccess?.()
})

vi.mock('../api/useApplyToTable', () => ({
  useApplyToTable: () => ({ mutate, isPending: false }),
}))

const testFile: StagedFile = { kind: 'new', localId: 'staged-1', name: 'ficha.pdf', file: new File(['contenido'], 'ficha.pdf') }

function renderDialog(overrides: Partial<ApplyToTableDialogProps> = {}) {
  render(
    // The provider is real, not a stub: FormDialog asks it before discarding a dirty form (#231), so
    // a dialog rendered without it throws rather than rendering.
    <ConfirmDialogProvider>
      <ApplyToTableDialog
        tableId="table-1"
        tableName="Curse of Strahd"
        open
        onOpenChange={vi.fn()}
        renderFilePicker={(onPick) => (
          // The real picker belongs to `features/files` and has its own suite; what matters here is
          // that whatever it hands back through `onPick` shows up staged, and later in the review step.
          <button type="button" onClick={() => onPick(testFile)}>
            Adjuntar prueba
          </button>
        )}
        commitStagedFiles={vi.fn().mockResolvedValue({ fileIds: [], failed: [], reused: [] })}
        {...overrides}
      />
    </ConfirmDialogProvider>,
  )
}

describe('ApplyToTableDialog', () => {
  /** #238, #247: a sent application cannot be edited, so the send button lives only past the review. */
  it('offers no send button on the writing step', () => {
    renderDialog()

    expect(screen.getByRole('button', { name: 'Siguiente' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Postularme' })).not.toBeInTheDocument()
  })

  it('the review step shows the message and the files that are about to be sent', async () => {
    renderDialog()

    await userEvent.type(screen.getByLabelText('Mensaje para el master'), 'Quiero jugar un mago')
    await userEvent.click(screen.getByRole('button', { name: 'Adjuntar prueba' }))
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }))

    expect(screen.getByText('Quiero jugar un mago')).toBeInTheDocument()
    expect(screen.getByText('ficha.pdf')).toBeInTheDocument()
    // And now the send button exists, past the review it just showed.
    expect(screen.getByRole('button', { name: 'Postularme' })).toBeInTheDocument()
  })

  it('goes back from the review step to the writing one without losing what was written', async () => {
    renderDialog()

    await userEvent.type(screen.getByLabelText('Mensaje para el master'), 'Una nota para el master')
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }))
    await userEvent.click(screen.getByRole('button', { name: 'Atrás' }))

    expect(screen.getByLabelText('Mensaje para el master')).toHaveValue('Una nota para el master')
  })

  it('allows applying without attaching any file', async () => {
    renderDialog()

    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }))
    await userEvent.click(screen.getByRole('button', { name: 'Postularme' }))

    await waitFor(() => expect(mutate).toHaveBeenCalledWith({ description: null, fileIds: [] }, expect.anything()))
  })

  it('uploads the attachments and sends the application with the ids that were uploaded', async () => {
    const commitStagedFiles = vi.fn().mockResolvedValue({ fileIds: ['file-1'], failed: [], reused: [] })
    renderDialog({ commitStagedFiles })

    await userEvent.click(screen.getByRole('button', { name: 'Adjuntar prueba' }))
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }))
    await userEvent.click(screen.getByRole('button', { name: 'Postularme' }))

    await waitFor(() => expect(commitStagedFiles).toHaveBeenCalledWith([testFile]))
    await waitFor(() => expect(mutate).toHaveBeenCalledWith({ description: null, fileIds: ['file-1'] }, expect.anything()))
  })
})
