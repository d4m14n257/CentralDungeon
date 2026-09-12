import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { FileDropzone } from './FileDropzone'

/** The input is `sr-only`, not absent: it is what makes the zone reachable by keyboard. */
function fileInput(): HTMLInputElement {
  const input = document.querySelector('input[type="file"]')
  if (!input) {
    throw new Error('The dropzone rendered no file input')
  }
  return input as HTMLInputElement
}

const pdf = (name = 'ficha.pdf') => new File(['hoja'], name, { type: 'application/pdf' })

describe('FileDropzone', () => {
  /** A limit somebody only meets by breaking it reads as a bug (principio 2 de frontend-diseno.md §1). */
  it('states the limits before anything is picked', () => {
    render(<FileDropzone onStaged={vi.fn()} />)

    expect(screen.getByText(/2 MB/)).toBeInTheDocument()
  })

  /**
   * **Nothing reaches the server here** (#238). The zone stages; the confirm that creates the table
   * or sends the answer is what uploads, so abandoning a flow halfway leaves nothing behind.
   */
  it('stages what was picked instead of uploading it', async () => {
    const onStaged = vi.fn()
    render(<FileDropzone onStaged={onStaged} />)

    const file = pdf()
    await userEvent.upload(fileInput(), file)

    expect(onStaged).toHaveBeenCalledWith(expect.objectContaining({ kind: 'new', name: 'ficha.pdf', file }))
  })

  /**
   * The cap is checked here now, and it has to be: with the upload deferred, the server's refusal
   * would otherwise arrive after four wizard steps.
   */
  it('refuses a file over the cap on the spot, and stages nothing', async () => {
    const onStaged = vi.fn()
    render(<FileDropzone onStaged={onStaged} />)

    const huge = new File([new Uint8Array(3 * 1024 * 1024)], 'mapa.png', { type: 'image/png' })
    await userEvent.upload(fileInput(), huge)

    expect(await screen.findByRole('alert')).toHaveTextContent('pesa más de lo permitido')
    expect(onStaged).not.toHaveBeenCalled()
  })

  it('refuses a type that is not on the whitelist, in the reader’s language', async () => {
    const onStaged = vi.fn()
    render(<FileDropzone onStaged={onStaged} />)

    await userEvent.upload(fileInput(), new File(['MZ'], 'cheat.exe', { type: 'application/x-msdownload' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Ese tipo de archivo no se acepta')
    expect(onStaged).not.toHaveBeenCalled()
  })

  /** Only the library asks which cajón; inside a flow the system already knows (#233). */
  it('does not ask what the file is unless the caller says there is no flow', () => {
    const { rerender } = render(<FileDropzone onStaged={vi.fn()} />)
    expect(screen.queryByRole('radiogroup')).not.toBeInTheDocument()

    rerender(<FileDropzone onStaged={vi.fn()} askForCategory categories={['PlayerApplication']} />)
    expect(screen.getByRole('radiogroup', { name: 'En qué cajón va' })).toBeInTheDocument()
  })

  /**
   * Chips and not a `<Select>`: this is a decision somebody makes before sending, so seeing every
   * option at once is what lets them make it without exploring a dropdown.
   */
  it('offers the cajones as chips, and only the ones it was given (#237)', () => {
    render(<FileDropzone onStaged={vi.fn()} askForCategory categories={['PlayerApplication', 'PlayerSubmission']} />)

    const group = screen.getByRole('radiogroup', { name: 'En qué cajón va' })
    expect(screen.getAllByRole('radio')).toHaveLength(2)
    expect(group).toHaveTextContent('Solicitud de jugador')
    expect(group).toHaveTextContent('Entrega del jugador')
    expect(group).not.toHaveTextContent('De mesa')
  })

  /** The input is cleared after a pick, so the same file can be removed and picked again. */
  it('clears the input so the same file can be picked twice', async () => {
    render(<FileDropzone onStaged={vi.fn()} />)

    await userEvent.upload(fileInput(), pdf())

    expect(fileInput().value).toBe('')
  })
})
