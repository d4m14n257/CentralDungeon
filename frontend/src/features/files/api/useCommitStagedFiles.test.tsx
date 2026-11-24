import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook } from '@testing-library/react'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { ApiError } from '@/types/api'
import type { StagedFile } from '@/types/file'
import { filesApi } from './filesApi'
import { useCommitStagedFiles } from './useCommitStagedFiles'

vi.mock('./filesApi', () => ({ filesApi: { upload: vi.fn() } }))
vi.mock('@/hooks/useClientLimits', () => ({
  useClientLimits: () => ({ maxFileSizeBytes: 2 * 1024 * 1024, claimTimeoutMinutes: 30, maxPlayersCap: 10 }),
}))

const wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={new QueryClient()}>{children}</QueryClientProvider>

function staged(name: string): StagedFile {
  return { kind: 'new', localId: name, name, file: new File(['x'], name) }
}

function refusal(errorCode: string, errorParams?: Record<string, string>) {
  return new ApiError(400, {
    title: 'Bad Request',
    status: 400,
    detail: 'English for a log',
    errorCode,
    ...(errorParams && { errorParams }),
  })
}

describe('useCommitStagedFiles', () => {
  beforeEach(() => vi.mocked(filesApi.upload).mockReset())

  async function commit(list: StagedFile[]) {
    const { result } = renderHook(() => useCommitStagedFiles(), { wrapper })
    let outcome!: Awaited<ReturnType<typeof result.current.mutateAsync>>
    await act(async () => {
      outcome = await result.current.mutateAsync({ staged: list })
    })
    return outcome
  }

  it('reports a refused file with the reason for the code the backend sent, and still links the one that went through', async () => {
    vi.mocked(filesApi.upload)
      .mockResolvedValueOnce({ file: { id: 'f1', name: 'ok.pdf' }, deduplicated: false } as never)
      .mockRejectedValueOnce(refusal('FILE_TOO_LARGE', { maxBytes: String(1024 * 1024), sizeBytes: String(9 * 1024 * 1024) }))

    const { fileIds, failed } = await commit([staged('ok.pdf'), staged('big.pdf')])

    expect(fileIds).toEqual(['f1'])
    expect(failed).toHaveLength(1)
    expect(failed[0]!.name).toBe('big.pdf')
    expect(failed[0]!.reason).toContain('1 MB')
  })

  it('gives a generic reason when the failure has no code to read', async () => {
    vi.mocked(filesApi.upload).mockRejectedValueOnce(new TypeError('Failed to fetch'))

    const { failed } = await commit([staged('a.pdf')])

    expect(failed).toEqual([{ name: 'a.pdf', reason: expect.stringContaining('No se pudo subir el archivo') }])
  })
})
