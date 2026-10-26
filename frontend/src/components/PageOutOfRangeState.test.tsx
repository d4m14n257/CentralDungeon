import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { PageOutOfRangeState } from './PageOutOfRangeState'

describe('PageOutOfRangeState', () => {
  it('says the page does not exist and goes back to the last one', async () => {
    const onPageChange = vi.fn()
    render(<PageOutOfRangeState totalPages={3} totalElements={61} onPageChange={onPageChange} />)

    expect(screen.getByText('Esa página no existe')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Ir a la última página' }))

    expect(onPageChange).toHaveBeenCalledWith(2)
  })
})
