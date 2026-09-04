import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { FileList } from './FileList'

const download = vi.hoisted(() => vi.fn())

vi.mock('@/api/client', async () => {
  const actual = await vi.importActual<typeof import('@/api/client')>('@/api/client')
  return { ...actual, api: { ...actual.api, download } }
})

function wrap(children: ReactNode) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
}

const files = [
  { fileId: 'file-1', name: 'ficha.pdf', mimeType: 'application/pdf', sizeBytes: 2048 },
  { fileId: 'file-2', name: 'mapa.png', mimeType: 'image/png', sizeBytes: 3 * 1024 * 1024 },
]

describe('FileList', () => {
  beforeEach(() => {
    download.mockReset()
    download.mockResolvedValue({ blob: new Blob(['x']), filename: 'ficha.pdf' })
    // jsdom implements neither of these, and the download path uses both.
    URL.createObjectURL = vi.fn(() => 'blob:test')
    URL.revokeObjectURL = vi.fn()
  })

  it('shows every file with its name and its size', () => {
    render(wrap(<FileList files={files} />))

    expect(screen.getByText('ficha.pdf')).toBeInTheDocument()
    expect(screen.getByText('2 KB')).toBeInTheDocument()
    expect(screen.getByText('mapa.png')).toBeInTheDocument()
    expect(screen.getByText('3 MB')).toBeInTheDocument()
  })

  /**
   * The download button is part of the list and not something each caller supplies: it is the one
   * action every reader of every one of these lists wants, and it needs an authenticated fetch
   * rather than a link.
   */
  it('gives every file a download action named in words, not only an icon', () => {
    render(wrap(<FileList files={files} />))

    expect(screen.getAllByRole('button', { name: 'Descargar' })).toHaveLength(2)
  })

  it('fetches the content when the download action is used', async () => {
    render(wrap(<FileList files={files} />))

    await userEvent.click(screen.getAllByRole('button', { name: 'Descargar' })[0]!)

    await waitFor(() => expect(download).toHaveBeenCalledWith('/api/v1/files/file-1/content', 'ficha.pdf'))
  })

  it('renders the extra content and actions a caller supplies', () => {
    render(
      wrap(
        <FileList
          files={files}
          renderMeta={(file) => <span>meta-{file.fileId}</span>}
          renderActions={(file) => <button type="button">borrar-{file.fileId}</button>}
        />,
      ),
    )

    expect(screen.getByText('meta-file-1')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'borrar-file-2' })).toBeInTheDocument()
  })
})
