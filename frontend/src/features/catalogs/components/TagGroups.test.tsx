import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import '@/providers/i18n'

import type { CatalogValue, TagCategory } from '../types'
import { TagGroups } from './TagGroups'

function tag(name: string, category: TagCategory | null): CatalogValue {
  return { id: name, name, status: 'Accepted', category, description: null }
}

describe('TagGroups', () => {
  it('groups the tags under their category, with the unclassified ones last', () => {
    render(
      <TagGroups
        tags={[tag('Misc', null), tag('One-shot', 'Format'), tag('Horror', 'Theme'), tag('Gothic', 'Theme')]}
        safetyHeading="Before you join"
      />,
    )

    const terms = screen.getAllByRole('term').map((term) => term.textContent)
    expect(terms).toEqual(['Temas', 'Formato y duración', 'Sin categoría'])
    expect(screen.getAllByText(/^(Gothic|Horror)$/).map((chip) => chip.textContent)).toEqual(['Gothic', 'Horror'])
  })

  it('puts content warnings and safety tools in a block of their own, under its heading', () => {
    render(
      <TagGroups
        tags={[tag('Horror', 'Theme'), tag('Gore', 'ContentWarning'), tag('X-card', 'SafetyTool')]}
        safetyHeading="Before you join"
      />,
    )

    const block = screen.getByRole('region', { name: 'Before you join' })
    expect(within(block).getByText('Gore')).toBeInTheDocument()
    expect(within(block).getByText('X-card')).toBeInTheDocument()
    expect(within(block).queryByText('Horror')).not.toBeInTheDocument()
  })

  it('leaves the safety block out when there is nothing to warn about', () => {
    render(<TagGroups tags={[tag('Horror', 'Theme')]} safetyHeading="Before you join" />)

    expect(screen.queryByRole('region')).not.toBeInTheDocument()
  })

  it('renders nothing for a table without tags', () => {
    const { container } = render(<TagGroups tags={[]} safetyHeading="Before you join" />)

    expect(container).toBeEmptyDOMElement()
  })
})
