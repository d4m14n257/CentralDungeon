import { render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { RichTextEditor } from './RichTextEditor'

function renderEditor(html: string) {
  render(<RichTextEditor value={html} onChange={() => {}} ariaLabel="editor" />)
  return screen.findByRole('textbox', { name: 'editor' })
}

describe('RichTextEditor', () => {
  it('keeps http(s) links and drops every other scheme, as the server does', async () => {
    const editor = await renderEditor(
      '<p><a href="https://ok.example/x">web</a> <a href="mailto:a@b.c">mail</a> <a href="tel:123">tel</a> <a href="javascript:alert(1)">js</a></p>',
    )
    await waitFor(() => expect(editor.querySelectorAll('a')).toHaveLength(1))
    expect(editor.querySelector('a')?.getAttribute('href')).toBe('https://ok.example/x')
    expect(editor.textContent).toContain('mail')
  })

  it('does not keep a pasted h4 as a heading, and keeps h1-h3', async () => {
    const editor = await renderEditor('<h4>deep</h4><h3>ok</h3>')
    await waitFor(() => expect(editor.querySelector('h3')).not.toBeNull())
    expect(editor.querySelector('h4')).toBeNull()
    expect(editor.textContent).toContain('deep')
  })
})
