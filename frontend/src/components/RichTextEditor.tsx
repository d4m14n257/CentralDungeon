import StarterKit from '@tiptap/starter-kit'
import { EditorContent, useEditor, type Editor } from '@tiptap/react'
import { BoldIcon, Code2Icon, Heading2Icon, ItalicIcon, ListIcon, ListOrderedIcon, QuoteIcon } from 'lucide-react'
import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

/** What the editor needs to know. */
export interface RichTextEditorProps {
  /** The current HTML. Controlled from the form, like every other field of it. */
  value: string
  /** Called with the HTML after each edit, or with an empty string when the content is emptied. */
  onChange: (html: string) => void
  /** Placeholder-ish label announced to assistive tech, since the editor is not a real textarea. */
  ariaLabel: string
  /** Extra classes for the wrapper. */
  className?: string
}

/**
 * The editor behind a table's description, its house rules and its requirements (#62).
 *
 * **TipTap and not TinyMCE**: the hosted TinyMCE the legacy used needs an API key, which is a
 * dependency on somebody else's account for a text box (frontend-diseno.md 6).
 *
 * The toolbar offers exactly what the backend's allowlist keeps, and nothing more. An editor that
 * can produce something the server strips is an editor that silently loses people's work — so the
 * two lists are the same list, and this comment is the reminder to change them together.
 *
 * Safety is not this component's job: what is typed here is sanitized server-side on the way in and
 * again on the way out. See `RichTextView` and `RichTextSanitizer`.
 *
 * @param props.value     the current HTML
 * @param props.onChange  called with the HTML after each edit
 * @param props.ariaLabel what the editing area is called, for assistive tech
 * @param props.className extra classes for the wrapper
 */
export function RichTextEditor({ value, onChange, ariaLabel, className }: RichTextEditorProps) {
  const { t } = useTranslation('common')
  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        // Not on the server's allowlist, so it must not be reachable here either.
        horizontalRule: false,
        codeBlock: false,
      }),
    ],
    content: value,
    editorProps: {
      attributes: {
        // The role is written out because a `contenteditable` div is not announced as a text field
        // on its own: Chrome exposes this one as a plain group, so a screen reader reaches an
        // editing area that never says it can be typed into. `aria-multiline` is the other half —
        // without it, assistive tech reads it as a single-line field and Enter means "submit".
        role: 'textbox',
        'aria-multiline': 'true',
        'aria-label': ariaLabel,
        class: 'min-h-32 px-3 py-2 focus:outline-none text-sm leading-6',
      },
    },
    onUpdate: ({ editor: instance }) => {
      // An empty document still serializes to "<p></p>"; sending that would store a paragraph
      // nobody wrote, and the field would stop reading as absent.
      onChange(instance.isEmpty ? '' : instance.getHTML())
    },
  })

  // Re-seeding from outside happens when the form resets or loads a table into the wizard. The
  // guard is what keeps it from fighting the person typing: without it every keystroke would
  // reset the selection to the start.
  useEffect(() => {
    if (editor && value !== editor.getHTML()) {
      editor.commands.setContent(value, { emitUpdate: false })
    }
  }, [editor, value])

  if (!editor) {
    return null
  }

  return (
    <div className={cn('border-border-strong bg-surface overflow-hidden rounded-md border', className)}>
      <div className="border-border bg-raised flex flex-wrap items-center gap-0.5 border-b p-1">
        <ToolbarButton
          editor={editor}
          label={t('richText.bold')}
          isActive={editor.isActive('bold')}
          onClick={() => editor.chain().focus().toggleBold().run()}
        >
          <BoldIcon className="size-4" aria-hidden="true" />
        </ToolbarButton>
        <ToolbarButton
          editor={editor}
          label={t('richText.italic')}
          isActive={editor.isActive('italic')}
          onClick={() => editor.chain().focus().toggleItalic().run()}
        >
          <ItalicIcon className="size-4" aria-hidden="true" />
        </ToolbarButton>
        <ToolbarButton
          editor={editor}
          label={t('richText.heading')}
          isActive={editor.isActive('heading', { level: 2 })}
          onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()}
        >
          <Heading2Icon className="size-4" aria-hidden="true" />
        </ToolbarButton>
        <ToolbarButton
          editor={editor}
          label={t('richText.bulletList')}
          isActive={editor.isActive('bulletList')}
          onClick={() => editor.chain().focus().toggleBulletList().run()}
        >
          <ListIcon className="size-4" aria-hidden="true" />
        </ToolbarButton>
        <ToolbarButton
          editor={editor}
          label={t('richText.orderedList')}
          isActive={editor.isActive('orderedList')}
          onClick={() => editor.chain().focus().toggleOrderedList().run()}
        >
          <ListOrderedIcon className="size-4" aria-hidden="true" />
        </ToolbarButton>
        <ToolbarButton
          editor={editor}
          label={t('richText.quote')}
          isActive={editor.isActive('blockquote')}
          onClick={() => editor.chain().focus().toggleBlockquote().run()}
        >
          <QuoteIcon className="size-4" aria-hidden="true" />
        </ToolbarButton>
        <ToolbarButton
          editor={editor}
          label={t('richText.code')}
          isActive={editor.isActive('code')}
          onClick={() => editor.chain().focus().toggleCode().run()}
        >
          <Code2Icon className="size-4" aria-hidden="true" />
        </ToolbarButton>
      </div>
      <EditorContent
        editor={editor}
        className="[&_ul]:list-disc [&_ul]:pl-5 [&_ol]:list-decimal [&_ol]:pl-5 [&_blockquote]:border-border-strong [&_blockquote]:border-l-2 [&_blockquote]:pl-3"
      />
    </div>
  )
}

/** What one toolbar button needs. Private to this file: the toolbar is not a reusable thing. */
interface ToolbarButtonProps {
  editor: Editor
  label: string
  isActive: boolean
  onClick: () => void
  children: React.ReactNode
}

/**
 * One formatting toggle. `aria-pressed` rather than a colour alone, because "is this bold on?" has
 * to be answerable without seeing the button.
 */
function ToolbarButton({ label, isActive, onClick, children }: ToolbarButtonProps) {
  return (
    <Button
      type="button"
      variant="ghost"
      size="sm"
      aria-label={label}
      aria-pressed={isActive}
      title={label}
      onClick={onClick}
      className={cn('size-8 p-0', isActive && 'bg-surface text-brand-fg')}
    >
      {children}
    </Button>
  )
}
