import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { ConfirmDialogProvider } from '@/components/ConfirmDialog'
import { acceptReview } from '@/test/review'
import { ApiError } from '@/types/api'

import type { AdminCatalogValue } from '../types'
import { TagDefinitionDialog } from './TagDefinitionDialog'

const defineTag = vi.hoisted(() => vi.fn())

vi.mock('../api/catalogsApi', () => ({ catalogsApi: { defineTag } }))

const HORROR: AdminCatalogValue = {
  id: 't-1',
  name: 'Horror',
  status: 'Accepted',
  category: 'Theme',
  description: 'Fear and dread.',
  canonicalId: null,
  canonicalName: null,
  uses: 0,
  aliasCount: 0,
  createdAt: '2026-09-01T00:00:00Z',
}

function renderDialog(onOpenChange = vi.fn()) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <ConfirmDialogProvider>
        <TagDefinitionDialog value={HORROR} open onOpenChange={onOpenChange} />
      </ConfirmDialogProvider>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  defineTag.mockReset()
  defineTag.mockResolvedValue(HORROR)
})

describe('TagDefinitionDialog', () => {
  it('opens on what the group already says, so the admin edits it rather than rewriting it', () => {
    renderDialog()

    expect(screen.getByLabelText('Definición')).toHaveValue('Fear and dread.')
    expect(screen.getByRole('combobox', { name: 'Categoría' })).toHaveTextContent('Temas')
  })

  it('sends the trimmed text after the review, and closes', async () => {
    const onOpenChange = vi.fn()
    renderDialog(onOpenChange)

    const field = screen.getByLabelText('Definición')
    await userEvent.clear(field)
    await userEvent.type(field, '  Fear, dread and the dark.  ')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    await acceptReview()

    await waitFor(() => expect(defineTag).toHaveBeenCalledWith('t-1', { category: 'Theme', description: 'Fear, dread and the dark.' }))
    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false))
  })

  it('sends an emptied definition as none rather than as blank text', async () => {
    renderDialog()

    await userEvent.clear(screen.getByLabelText('Definición'))
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    await acceptReview()

    await waitFor(() => expect(defineTag).toHaveBeenCalledWith('t-1', { category: 'Theme', description: null }))
  })

  it('says under the form when the tag turned out to be an alias, keeping what was typed', async () => {
    defineTag.mockRejectedValue(
      new ApiError(409, { title: 'Conflict', status: 409, detail: 'for the log', errorCode: 'CATALOG_VALUE_NOT_CANONICAL' }),
    )
    renderDialog()

    await userEvent.type(screen.getByLabelText('Definición'), ' More.')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    await acceptReview()

    expect(await screen.findByRole('alert')).toHaveTextContent('Ese valor es un equivalente de otro grupo.')
    expect(screen.getByLabelText('Definición')).toHaveValue('Fear and dread. More.')
  })
})
