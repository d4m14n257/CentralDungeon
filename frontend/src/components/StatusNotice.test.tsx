import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { StatusNotice } from './StatusNotice'

describe('StatusNotice', () => {
  it('says the hint in a line of its own under the description', () => {
    render(<StatusNotice title="Where it stands" description="What follows." hint="Why a button is off." />)

    const description = screen.getByText('What follows.')
    const hint = screen.getByText('Why a button is off.')
    expect(hint).not.toBe(description)
    expect(description.compareDocumentPosition(hint) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  })

  it('draws no hint line when there is none', () => {
    render(<StatusNotice title="Where it stands" description="What follows." />)

    expect(screen.getByText('What follows.').parentElement?.querySelectorAll('p')).toHaveLength(1)
  })
})
