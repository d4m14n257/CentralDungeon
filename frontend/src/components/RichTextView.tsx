import { cn } from '@/lib/utils'

/** What a rich-text block needs to render itself. */
export interface RichTextViewProps {
  /** The stored HTML, as the API sent it. Null or empty renders nothing at all. */
  html: string | null | undefined
  /** Extra classes for the wrapper, for the rare block that needs a different width or spacing. */
  className?: string
}

/**
 * Renders the rich text a master wrote — a table's description, its house rules, its requirements.
 *
 * **The safety of this component is the server's** (#62). What arrives has been through the
 * allowlist sanitizer on the way in *and* on the way out, which is why `dangerouslySetInnerHTML` is
 * the right call here rather than a smell: re-sanitizing in the browser would put a second policy
 * in a second language in front of the same text, and the one that matters is the one the attacker
 * cannot reach.
 *
 * The typography is spelled out per element instead of leaning on a prose plugin: the design
 * system's tokens are the source of every value (#118, #130), and a plugin would bring its own.
 *
 * @param props.html      the stored HTML
 * @param props.className extra classes for the wrapper
 */
export function RichTextView({ html, className }: RichTextViewProps) {
  if (!html) {
    return null
  }

  return (
    <div
      className={cn(
        'text-sm leading-6 [&_a]:text-brand-fg [&_a]:underline',
        '[&_p]:mb-2 [&_p:last-child]:mb-0',
        '[&_h1]:font-serif [&_h1]:text-lg [&_h1]:font-semibold [&_h2]:font-serif [&_h2]:text-base [&_h2]:font-semibold [&_h3]:font-medium',
        '[&_ul]:mb-2 [&_ul]:list-disc [&_ul]:pl-5 [&_ol]:mb-2 [&_ol]:list-decimal [&_ol]:pl-5',
        '[&_blockquote]:border-border-strong [&_blockquote]:text-fg-muted [&_blockquote]:border-l-2 [&_blockquote]:pl-3',
        '[&_code]:bg-raised [&_code]:rounded [&_code]:px-1 [&_code]:py-0.5 [&_code]:text-xs',
        className,
      )}
      dangerouslySetInnerHTML={{ __html: html }}
    />
  )
}
