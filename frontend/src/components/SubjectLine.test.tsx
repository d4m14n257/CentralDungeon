import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'

import { SubjectLine } from './SubjectLine'

function renderLine(props: Parameters<typeof SubjectLine>[0]) {
  return render(
    <MemoryRouter>
      <SubjectLine {...props} />
    </MemoryRouter>,
  )
}

describe('SubjectLine', () => {
  it('says the subject and links where the item points', () => {
    renderLine({ subject: 'About: Diego', link: { to: '/admin/tables/t1/players', label: 'View the table and its players' } })

    expect(screen.getByText('About: Diego')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'View the table and its players' })).toHaveAttribute('href', '/admin/tables/t1/players')
  })

  it('draws only the link when there is no subject', () => {
    renderLine({ link: { to: '/admin/tables/t1', label: 'View the table' } })

    expect(screen.getByRole('link', { name: 'View the table' })).toBeInTheDocument()
  })

  it('draws nothing when it has neither', () => {
    const { container } = renderLine({ subject: null, link: null })

    expect(container).toBeEmptyDOMElement()
  })
})
