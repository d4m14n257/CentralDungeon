import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook } from '@testing-library/react'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { ApiError } from '@/types/api'
import type { StagedFile } from '@/types/file'
import { filesApi } from './filesApi'
import { useUploadToLibrary } from './useUploadToLibrary'

vi.mock('./filesApi', () => ({ filesApi: { uploadToLibrary: vi.fn() } }))
vi.mock('@/hooks/useClientLimits', () => ({
  useClientLimits: () => ({ maxFileSizeBytes: 2 * 1024 * 1024, claimTimeoutMinutes: 30, maxPlayersCap: 10 }),
}))

const wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={new QueryClient()}>{children}</QueryClientProvider>

function upload(name: string) {
  const staged: StagedFile = { kind: 'new', localId: name, name, file: new File(['x'], name) }
  return { staged, category: 'Announcement' as const }
}

describe('useUploadToLibrary', () => {
  beforeEach(() => vi.mocked(filesApi.uploadToLibrary).mockReset())

  async function send(uploads: ReturnType<typeof upload>[]) {
    const { result } = renderHook(() => useUploadToLibrary(), { wrapper })
    let outcome!: Awaited<ReturnType<typeof result.current.mutateAsync>>
    await act(async () => {
      outcome = await result.current.mutateAsync({ uploads, publish: true })
    })
    return outcome
  }

  it('publishes the accepted file and reports the refused one with the reason for its code', async () => {
    vi.mocked(filesApi.uploadToLibrary)
      .mockResolvedValueOnce({ deduplicated: false } as never)
      .mockRejectedValueOnce(
        new ApiError(400, { title: 'Bad Request', status: 400, detail: 'English for a log', errorCode: 'FILE_TYPE_NOT_ALLOWED' }),
      )

    const { published, failed } = await send([upload('ok.pdf'), upload('virus.exe')])

    expect(published).toEqual(['ok.pdf'])
    expect(failed).toEqual([{ name: 'virus.exe', reason: expect.stringContaining('no se acepta') }])
  })

  it('gives a generic reason for a server error with no code', async () => {
    vi.mocked(filesApi.uploadToLibrary).mockRejectedValueOnce(
      new ApiError(500, { title: 'Internal Server Error', status: 500, detail: 'boom', errorCode: '' }),
    )

    const { failed } = await send([upload('a.pdf')])

    expect(failed).toEqual([{ name: 'a.pdf', reason: expect.stringContaining('No se pudo subir el archivo') }])
  })
})
