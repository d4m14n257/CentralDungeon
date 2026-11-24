/** What the preview dialog knows how to draw. Anything else is download-only. */
export type PreviewKind = 'image' | 'pdf'

/** The extensions that stand for a kind when the declared MIME type says nothing useful. */
const EXTENSION_KINDS: Record<string, PreviewKind> = {
  png: 'image',
  jpg: 'image',
  jpeg: 'image',
  gif: 'image',
  webp: 'image',
  pdf: 'pdf',
}

/** MIME types that carry no information, so the filename's extension decides instead. */
const GENERIC_MIME_TYPES = new Set(['', 'application/octet-stream'])

/**
 * Whether a file can be previewed in the app, and as what.
 *
 * Decided by the declared MIME type; only when that is missing or generic does the extension of the
 * name decide. Images and PDFs are the only kinds a browser draws without help.
 *
 * @param mimeType the declared MIME type
 * @param name     the original filename
 * @returns the kind to draw, or null when the file can only be downloaded
 */
export function previewKindOf(mimeType: string | undefined, name: string): PreviewKind | null {
  const mime = (mimeType ?? '').toLowerCase()
  if (mime.startsWith('image/')) {
    return 'image'
  }
  if (mime === 'application/pdf') {
    return 'pdf'
  }
  if (!GENERIC_MIME_TYPES.has(mime)) {
    return null
  }
  const extension = name.includes('.') ? name.slice(name.lastIndexOf('.') + 1).toLowerCase() : ''
  return EXTENSION_KINDS[extension] ?? null
}
