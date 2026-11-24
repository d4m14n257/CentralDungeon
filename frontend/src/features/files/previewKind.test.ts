import { describe, expect, it } from 'vitest'

import { previewKindOf } from './previewKind'

describe('previewKindOf', () => {
  it('previews images and PDFs by their MIME type', () => {
    expect(previewKindOf('image/png', 'mapa.png')).toBe('image')
    expect(previewKindOf('application/pdf', 'ficha.pdf')).toBe('pdf')
  })

  it('offers nothing for the rest, whatever the extension says', () => {
    expect(previewKindOf('application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'ficha.docx')).toBeNull()
    expect(previewKindOf('application/msword', 'lies.pdf')).toBeNull()
  })

  it('falls back to the extension only when the MIME type is missing or generic', () => {
    expect(previewKindOf(undefined, 'mapa.JPG')).toBe('image')
    expect(previewKindOf('application/octet-stream', 'ficha.pdf')).toBe('pdf')
    expect(previewKindOf('', 'notas')).toBeNull()
  })
})
