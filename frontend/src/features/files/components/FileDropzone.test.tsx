import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { ApiError } from '@/types/api'
import { FileDropzone } from './FileDropzone'

const upload = vi.hoisted(() => vi.fn())

vi.mock('../api/filesApi', () => ({ filesApi: { upload } }))

function wrap(children: ReactNode) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
}

/** The input is `sr-only`, not absent: it is what makes the zone reachable by keyboard. */
function fileInput(): HTMLInputElement {
  const input = document.querySelector('input[type="file"]')
  if (!input) {
    throw new Error('The dropzone rendered no file input')
  }
  return input as HTMLInputElement
}

const pdf = () => new File(['hoja'], 'ficha.pdf', { type: 'application/pdf' })

describe('FileDropzone', () => {
  beforeEach(() => {
    upload.mockReset().mockResolvedValue({ file: { id: 'file-1', name: 'ficha.pdf' }, deduplicated: false })
  })

  /** Only the library asks which cajón; inside a flow the system already knows (#233). */
  it('does not ask what the file is unless the caller says there is no flow', async () => {
    const { rerender } = render(wrap(<FileDropzone onUploaded={vi.fn()} />))
    expect(screen.queryByLabelText('En qué cajón va')).not.toBeInTheDocument()

    rerender(wrap(<FileDropzone onUploaded={vi.fn()} askForCategory categories={['PlayerApplication']} />))
    expect(screen.getByLabelText('En qué cajón va')).toBeInTheDocument()
  })

  /**
   * It offers only the cajones it was given (#237), and files under the first of them.
   *
   * A plain member of the community never files table material, and `Announcement` is nobody's — so
   * a select that offered either would offer something the server refuses.
   */
  it('files under the first cajón it was offered, and never one it was not', async () => {
    const onUploaded = vi.fn()
    render(wrap(<FileDropzone onUploaded={onUploaded} askForCategory categories={['PlayerApplication', 'PlayerSubmission']} />))

    await userEvent.upload(fileInput(), pdf())

    await waitFor(() => expect(upload).toHaveBeenCalled())
    expect(upload.mock.calls[0]?.[1]).toEqual({ fileType: 'Private', fileCategory: 'PlayerApplication' })
  })

  /** A limit somebody only meets by breaking it reads as a bug (principio 2 de frontend-diseno.md §1). */
  it('states the limits before anything is uploaded', () => {
    render(wrap(<FileDropzone onUploaded={vi.fn()} />))

    expect(screen.getByText(/2 MB/)).toBeInTheDocument()
  })

  it('uploads what was picked and hands the file back', async () => {
    const onUploaded = vi.fn()
    render(wrap(<FileDropzone onUploaded={onUploaded} />))

    const file = pdf()
    await userEvent.upload(fileInput(), file)

    await waitFor(() => expect(onUploaded).toHaveBeenCalledWith({ id: 'file-1', name: 'ficha.pdf' }))
    // `Private`, so the upload lands in the reuse history of #65 — a history nothing ever enters is
    // one nobody reuses from (#68). And no cajón: without `askForCategory` there is a flow behind
    // this zone, and the link that follows is what classifies the file (#233).
    expect(upload).toHaveBeenCalledWith(file, { fileType: 'Private', fileCategory: null })
  })

  /**
   * #234, which is the whole reason the status is read: recognising an upload is the cheapest lever
   * of #75, and until it was said out loud it happened in complete silence.
   */
  it('says so when the file was recognised instead of stored again', async () => {
    upload.mockResolvedValue({ file: { id: 'file-1', name: 'ficha.pdf' }, deduplicated: true })
    const onUploaded = vi.fn()
    render(wrap(<FileDropzone onUploaded={onUploaded} />))

    await userEvent.upload(fileInput(), pdf())

    expect(await screen.findByText(/ya lo tenías subido/)).toBeInTheDocument()
    // Still a success: to whoever is uploading, the two outcomes are the same one.
    expect(onUploaded).toHaveBeenCalledWith({ id: 'file-1', name: 'ficha.pdf' })
  })

  /**
   * The message belongs under the zone, where the person is looking and where it stays while they
   * pick another file — not in a toast that is gone in four seconds.
   */
  it('shows a refused upload inline, in the reader’s language', async () => {
    upload.mockRejectedValue(
      new ApiError(400, {
        title: 'Bad Request',
        status: 400,
        detail: 'MIME type application/x-msdownload is not accepted',
        errorCode: 'FILE_TYPE_NOT_ALLOWED',
      }),
    )
    render(wrap(<FileDropzone onUploaded={vi.fn()} />))

    await userEvent.upload(fileInput(), new File(['MZ'], 'cheat.exe', { type: 'application/x-msdownload' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Ese tipo de archivo no se acepta')
    // The backend's own English sentence is for a log and never for a person (#197).
    expect(alert).not.toHaveTextContent('is not accepted')
  })

  /**
   * The input is cleared even when the upload failed. Otherwise it keeps the rejected file, picking
   * the same one again fires no change event, and somebody who fixed what the message told them to
   * fix finds the control silently dead.
   */
  it('clears the input after a failure so the same file can be picked again', async () => {
    upload.mockRejectedValue(new ApiError(400, { title: 'Bad Request', status: 400, detail: 'too large', errorCode: 'FILE_TOO_LARGE' }))
    render(wrap(<FileDropzone onUploaded={vi.fn()} />))

    await userEvent.upload(fileInput(), pdf())

    await waitFor(() => expect(fileInput().value).toBe(''))
  })

  /** An unknown failure still says something, rather than leaving the zone looking idle. */
  it('falls back to a generic message when the failure carries no known code', async () => {
    upload.mockRejectedValue(new Error('network is down'))
    render(wrap(<FileDropzone onUploaded={vi.fn()} />))

    await userEvent.upload(fileInput(), pdf())

    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo subir el archivo')
  })
})
