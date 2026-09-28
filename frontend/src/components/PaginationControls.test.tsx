import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { PaginationControls } from './PaginationControls'

function renderPager(props: Partial<Parameters<typeof PaginationControls>[0]> = {}) {
  const onPageChange = vi.fn()
  const onPageSizeChange = vi.fn()
  render(
    <PaginationControls
      page={0}
      totalPages={40}
      totalElements={1000}
      onPageChange={onPageChange}
      pageSize={25}
      onPageSizeChange={onPageSizeChange}
      {...props}
    />,
  )
  return { onPageChange, onPageSizeChange }
}

/** The numbered strip, as the labels of its page buttons. */
function stripPages() {
  return within(screen.getByRole('list'))
    .getAllByRole('button')
    .map((button) => button.textContent)
}

describe('PaginationControls', () => {
  /** #271: the ends and the neighbours of the current page, with the rest folded into gaps. */
  it('shows the first, the last and the neighbours of the current page', () => {
    renderPager({ page: 19 })

    expect(stripPages()).toEqual(['1', '19', '20', '21', '40'])
    expect(screen.getByRole('button', { name: 'Página 20' })).toHaveAttribute('aria-current', 'page')
  })

  /** A gap of one page shows that page: `1 … 3` would hide less than it costs. */
  it('fills a one-page gap with the page itself', () => {
    renderPager({ page: 2, totalPages: 10 })

    expect(stripPages()).toEqual(['1', '2', '3', '4', '10'])
  })

  /** The point of #271: page 900 is a number and an Enter, not 899 clicks. */
  it('jumps straight to a typed page, clamped into range', async () => {
    const { onPageChange } = renderPager({ totalPages: 1000, totalElements: 25000 })

    await userEvent.type(screen.getByRole('spinbutton'), '900{Enter}')
    expect(onPageChange).toHaveBeenLastCalledWith(899)

    await userEvent.type(screen.getByRole('spinbutton'), '5000{Enter}')
    expect(onPageChange).toHaveBeenLastCalledWith(999)
  })

  /** Picking 100 must not hide the control that brings you back to 10. */
  it('stays visible on a single page while there is a size to change', () => {
    renderPager({ totalPages: 1, totalElements: 30, pageSize: 100 })

    expect(screen.getByRole('navigation')).toBeInTheDocument()
    expect(screen.queryByRole('spinbutton')).not.toBeInTheDocument()
  })

  it('hides itself on a single page when the size is not the reader to choose', () => {
    render(<PaginationControls page={0} totalPages={1} totalElements={30} onPageChange={vi.fn()} />)

    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
  })
})
