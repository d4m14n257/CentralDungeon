import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { ThemeCheck } from '@/ThemeCheck'

describe('ThemeCheck', () => {
  it('renders one badge per state token', () => {
    render(<ThemeCheck />)
    expect(screen.getByText('draft')).toBeInTheDocument()
    expect(screen.getByText('blocked')).toBeInTheDocument()
  })
})
