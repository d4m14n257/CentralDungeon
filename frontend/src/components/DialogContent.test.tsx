import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import '@/providers/i18n'
import { Dialog, DialogDescription, DialogTitle } from '@/components/ui/dialog'

import { DialogContent } from './DialogContent'

describe('DialogContent', () => {
  /** The generated close button says "Close" whatever the interface language is. */
  it('names its close button in the reader’s language, with a single close button', () => {
    render(
      <Dialog open>
        <DialogContent>
          <DialogTitle>Title</DialogTitle>
          <DialogDescription>Body</DialogDescription>
        </DialogContent>
      </Dialog>,
    )

    expect(screen.getByRole('button', { name: 'Cerrar' })).toBeInTheDocument()
    expect(screen.queryByText('Close')).not.toBeInTheDocument()
  })
})
