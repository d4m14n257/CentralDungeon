import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { FilePicker } from './FilePicker'

const upload = vi.hoisted(() => vi.fn())
const listMine = vi.hoisted(() => vi.fn())
const listPublic = vi.hoisted(() => vi.fn())

vi.mock('../api/filesApi', () => ({ filesApi: { upload, listMine, listPublic } }))

function wrap(children: ReactNode) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
}

const emptyPage = { content: [], page: 0, size: 8, totalElements: 0, totalPages: 0 }

/**
 * The dropzone's file input, which is `sr-only` rather than absent: a `<div>` with an `onClick`
 * would look the same on screen and be unreachable by keyboard, and no browser opens a file dialog
 * for a synthetic click.
 */
function dropzoneInput(): HTMLInputElement {
  const input = document.querySelector('input[type="file"]')
  if (!input) {
    throw new Error('The dropzone rendered no file input')
  }
  return input as HTMLInputElement
}

describe('FilePicker', () => {
  beforeEach(() => {
    // The mutation answers with the file *and* whether it was recognised rather than written (#234).
    upload.mockReset().mockResolvedValue({ file: { id: 'file-new', name: 'ficha.pdf' }, deduplicated: false })
    listMine.mockReset().mockResolvedValue(emptyPage)
    listPublic.mockReset().mockResolvedValue(emptyPage)
  })

  /**
   * #65 and #75's whole strategy: reuse only reduces anything if it is as reachable as uploading.
   * If it were a link tucked under an upload box, everybody would upload.
   */
  it('offers uploading and reusing as peers', () => {
    render(wrap(<FilePicker onPick={vi.fn()} />))

    expect(screen.getByRole('tab', { name: 'Subir' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Mis archivos' })).toBeInTheDocument()
  })

  /** A limit somebody only meets by breaking it reads as a bug (principio 2). */
  it('states the per-file limit before anything is uploaded', () => {
    render(wrap(<FilePicker onPick={vi.fn()} />))

    expect(screen.getByText(/2 MB/)).toBeInTheDocument()
  })

  /** The published tab is what makes #79 reachable — and it is absent when nothing offers it. */
  it('offers the published files only when the caller asks for them', () => {
    const { rerender } = render(wrap(<FilePicker onPick={vi.fn()} />))
    expect(screen.queryByRole('tab', { name: 'Publicados' })).not.toBeInTheDocument()

    rerender(wrap(<FilePicker onPick={vi.fn()} offerPublished />))
    expect(screen.getByRole('tab', { name: 'Publicados' })).toBeInTheDocument()
  })

  /**
   * #233: the published tab is asked for the cajón the picker stands in, and never for an audience.
   * A master attaching to their table wants what the community published for tables; the same master
   * writing a request wants what it published for requests. One control, two answers.
   */
  it('asks the published tab for the cajón it is standing in', async () => {
    render(wrap(<FilePicker onPick={vi.fn()} offerPublished cajon="TableMaterial" />))

    await userEvent.click(screen.getByRole('tab', { name: 'Publicados' }))

    // The cajón is the flow, so a master attaching to their table gets the blanks published *for*
    // that moment - which is what the audience of #64 could never express (#233).
    await waitFor(() => expect(listPublic).toHaveBeenCalledWith('TableMaterial'))
  })

  /**
   * **Picking stages, it does not upload** (#238). The confirm that creates the table or sends the
   * answer is what puts bytes on the server, so abandoning the flow leaves nothing behind.
   */
  it('stages what was picked and uploads nothing', async () => {
    const onPick = vi.fn()
    render(wrap(<FilePicker onPick={onPick} />))

    const file = new File(['hoja'], 'ficha.pdf', { type: 'application/pdf' })
    await userEvent.upload(dropzoneInput(), file)

    expect(onPick).toHaveBeenCalledWith(expect.objectContaining({ kind: 'new', name: 'ficha.pdf', file }))
    expect(upload).not.toHaveBeenCalled()
  })

  it('picks a file from the history without uploading anything', async () => {
    listMine.mockResolvedValue({
      ...emptyPage,
      content: [
        {
          id: 'file-old',
          name: 'ficha-vieja.pdf',
          mimeType: 'application/pdf',
          sizeBytes: 1024,
          categories: ['TableMaterial'],
          lastUsedAt: null,
          usages: [],
        },
      ],
      totalElements: 1,
      totalPages: 1,
    })
    const onPick = vi.fn()
    render(wrap(<FilePicker onPick={onPick} />))

    await userEvent.click(screen.getByRole('tab', { name: 'Mis archivos' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Usar' }))

    // The name travels with the id: a caller that gathers several files before sending them shows
    // them back by name, and looking that up again for something the picker just had is a round trip
    // for nothing.
    // Something reused arrives already carrying its id: there is nothing to upload for it (#65).
    expect(onPick).toHaveBeenCalledWith({ kind: 'existing', fileId: 'file-old', name: 'ficha-vieja.pdf' })
    expect(upload).not.toHaveBeenCalled()
  })

  it('explains an empty history instead of showing nothing', async () => {
    render(wrap(<FilePicker onPick={vi.fn()} />))

    await userEvent.click(screen.getByRole('tab', { name: 'Mis archivos' }))

    expect(await screen.findByText('Todavía no subiste nada')).toBeInTheDocument()
  })
})
