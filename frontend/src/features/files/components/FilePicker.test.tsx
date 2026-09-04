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

describe('FilePicker', () => {
  beforeEach(() => {
    upload.mockReset().mockResolvedValue({ id: 'file-new', name: 'ficha.pdf' })
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
   * #79's own example, as a regression test: the community's default sheet is published *for
   * players* and the master is the one attaching it, so a picker that narrowed by its own reader's
   * role would hide exactly the file the feature exists to share.
   */
  it('asks for every published file unless the caller narrows the audience', async () => {
    render(wrap(<FilePicker onPick={vi.fn()} offerPublished />))

    await userEvent.click(screen.getByRole('tab', { name: 'Publicados' }))

    await waitFor(() => expect(listPublic).toHaveBeenCalledWith(undefined))
  })

  it('uploads what was picked and hands the caller the resulting file', async () => {
    const onPick = vi.fn()
    render(wrap(<FilePicker onPick={onPick} />))

    const file = new File(['hoja'], 'ficha.pdf', { type: 'application/pdf' })
    await userEvent.upload(screen.getByLabelText('Elegir un archivo para subir'), file)

    await waitFor(() => expect(onPick).toHaveBeenCalledWith({ fileId: 'file-new', name: 'ficha.pdf' }))
    expect(upload).toHaveBeenCalledWith(file, { fileType: 'Private' })
  })

  /**
   * `Private` and not `SingleUse`: somebody who bothered to upload a sheet will want it on the next
   * table, and the history of #65 is empty unless uploads land in it (#68).
   */
  it('keeps what was uploaded in the reuse history', async () => {
    render(wrap(<FilePicker onPick={vi.fn()} />))

    await userEvent.upload(
      screen.getByLabelText('Elegir un archivo para subir'),
      new File(['hoja'], 'ficha.pdf', { type: 'application/pdf' }),
    )

    await waitFor(() => expect(upload).toHaveBeenCalled())
    expect(upload.mock.calls[0]?.[1]).toEqual({ fileType: 'Private' })
  })

  it('picks a file from the history without uploading anything', async () => {
    listMine.mockResolvedValue({
      ...emptyPage,
      content: [{ id: 'file-old', name: 'ficha-vieja.pdf', mimeType: 'application/pdf', sizeBytes: 1024 }],
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
    expect(onPick).toHaveBeenCalledWith({ fileId: 'file-old', name: 'ficha-vieja.pdf' })
    expect(upload).not.toHaveBeenCalled()
  })

  it('explains an empty history instead of showing nothing', async () => {
    render(wrap(<FilePicker onPick={vi.fn()} />))

    await userEvent.click(screen.getByRole('tab', { name: 'Mis archivos' }))

    expect(await screen.findByText('Todavía no subiste nada')).toBeInTheDocument()
  })
})
