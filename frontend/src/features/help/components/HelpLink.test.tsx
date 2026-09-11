import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { HELP_SECTIONS } from '../sections/registry'
import { HelpLink } from './HelpLink'

/**
 * #231: the help stopped being a route so that reading it would stop costing the work in progress.
 * These are the two properties that has to hold on to.
 */
describe('HelpLink', () => {
  it('opens the section in a dialog instead of navigating', async () => {
    render(<HelpLink section="masters.schedule">Cómo funcionan los horarios</HelpLink>)

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Cómo funcionan los horarios' }))

    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })

  /**
   * Most of these sit inside a form, and a bare `<button>` in one submits it - which would create
   * the table the reader was only asking a question about.
   */
  it('does not submit the form it is standing in', async () => {
    const onSubmit = vi.fn((event: React.FormEvent) => event.preventDefault())
    render(
      <form onSubmit={onSubmit}>
        <HelpLink section="masters.schedule">Ayuda</HelpLink>
      </form>,
    )

    await userEvent.click(screen.getByRole('button', { name: 'Ayuda' }))

    expect(onSubmit).not.toHaveBeenCalled()
  })
})

/**
 * Every screen names its section by id, and a section that lost its body would only be noticed by
 * whoever opened that particular dialog.
 */
describe('the help registry', () => {
  it('gives every section a title and a body', () => {
    for (const [id, section] of Object.entries(HELP_SECTIONS)) {
      expect(section.titleKey, id).toBeTruthy()
      expect(typeof section.Body, id).toBe('function')
    }
  })
})
