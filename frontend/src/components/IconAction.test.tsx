import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { IconAction } from './IconAction'

describe('IconAction', () => {
  it('runs its action when nothing is in the way', async () => {
    const onClick = vi.fn()
    render(<IconAction label="Publish" icon={<span />} onClick={onClick} />)

    await userEvent.click(screen.getByRole('button', { name: 'Publish' }))

    expect(onClick).toHaveBeenCalledOnce()
  })

  it('with a reason, does nothing and says why instead of going silent', async () => {
    const onClick = vi.fn()
    render(<IconAction label="Publish" icon={<span />} onClick={onClick} disabledReason="Choose what it is first" />)
    const button = screen.getByRole('button', { name: 'Publish' })

    await userEvent.click(button)

    expect(onClick).not.toHaveBeenCalled()
    expect(button).toHaveAttribute('aria-disabled', 'true')
    expect(button).toHaveAccessibleDescription('Choose what it is first')
  })
})
