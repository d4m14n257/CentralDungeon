import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/types/api'

import { QueryErrorState } from './QueryErrorState'

function failure(status: number) {
  return new ApiError(status, { title: 'Error', status, detail: 'detail', errorCode: 'ERROR' })
}

describe('QueryErrorState', () => {
  /** A 404 is an answer: nothing failed, and trying again cannot change it. */
  it('says a 404 does not exist and offers no retry', () => {
    render(<QueryErrorState error={failure(404)} onRetry={vi.fn()} />)

    expect(screen.getByText('No existe')).toBeInTheDocument()
    expect(screen.queryByText('Algo salió mal')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument()
  })

  it('explains a 403 without a retry', () => {
    render(<QueryErrorState error={failure(403)} onRetry={vi.fn()} />)

    expect(screen.getByText('No tenés permiso')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument()
  })

  it('offers the retry for any other failure', async () => {
    const onRetry = vi.fn()
    render(<QueryErrorState error={failure(500)} onRetry={onRetry} />)

    expect(screen.getByText('Algo salió mal')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(onRetry).toHaveBeenCalledOnce()
  })

  it('treats an error that is not an ApiError as a failure worth retrying', () => {
    render(<QueryErrorState error={new TypeError('network')} onRetry={vi.fn()} />)

    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument()
  })
})
