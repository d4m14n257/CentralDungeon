import { Fragment, useId, useRef, useState, type KeyboardEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { CornerDownLeft, FilterX, X } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { HelpLink } from '@/features/help'
import { cn } from '@/lib/utils'
import {
  OPEN_FIELD_PREFIX,
  buildSearchQuery,
  commitSearchDraft,
  leadingConnector,
  openValueOf,
  searchQueryOf,
  toTerms,
  type SearchConnector,
  type SearchField,
  type SearchQueryValue,
  type SearchTerm,
} from '@/lib/searchQuery'

interface SearchQueryInputProps {
  /** The fields this box accepts behind a `/`. Their order is the order of the suggestions. */
  fields: readonly SearchField[]
  /** The box's state; it keeps none of its own, so the screen owns it (normally via `useSearchQuery`). */
  value: SearchQueryValue
  /** Called with every change, typed or clicked. None of them is a search: that is {@link onSearch} (#268). */
  onChange: (value: SearchQueryValue) => void
  /**
   * The canonical query the screen's results currently answer. Compared with the chips on screen to
   * tell the reader there is an edit that has not been searched yet.
   */
  searchedQuery: string
  /**
   * Asks the screen to search the given canonical query: Enter on an empty text, or «clear filters».
   * The only way a search leaves the box (#268).
   */
  onSearch: (query: string) => void
  /** Shown while the box is empty. */
  placeholder?: string
  /** The accessible name of the box and of its suggestion list. */
  label: string
  /** Whether the box takes focus when it mounts, as it does inside a picker dialog. */
  autoFocus?: boolean
  /**
   * The canonical query the screen shows when nobody has searched anything, and what «clear filters»
   * goes back to (#268). Empty for most screens; the admin requests tray opens on what is pending.
   */
  defaultQuery?: string
  /**
   * Whether the screen has filters of its own beside the box that are away from their default, so
   * «clear filters» is offered even when the box itself is untouched — the category of `/admin/files`
   * (#233).
   */
  extraFiltersActive?: boolean
  /** Puts those filters beside the box back to their default. Called together with the box's own reset. */
  onClearExtraFilters?: () => void
}

type Suggestion =
  | { kind: 'field'; name: string; label: string }
  | { kind: 'connector'; name: string; label: string; connector: SearchConnector }
  /** One of a field's fixed values (#164), and where in the text it goes when it is picked. */
  | { kind: 'value'; name: string; label: string; start: number }

/**
 * The application's one search box (decisiones.md #164): it is typed on a single line, and each
 * closed criterion becomes a chip so that what is being searched, and by which field, stays in view.
 *
 * It knows no domain, on purpose — it receives the fields it accepts rather than knowing them
 * (arquitectura-frontend skill §3.1.1). Everything that differs between the people search and the file searches
 * arrives as a prop; there is no second implementation of any of this.
 *
 * **The slash is the only separator, and Enter is the only thing that closes a criterion** (#240).
 * Picking a command from the list writes it into the text exactly as typing it by hand would —
 * `/user_name ` and the cursor after it — and nothing becomes a chip until Enter. Before #240 the two
 * ways parted company: picking pinned a chip straight away while typing the same command left plain
 * text, so the same query behaved differently depending on which screen taught you to enter it.
 * Commas separate alternatives within one criterion. None of this uses bare reserved words: an "or"
 * typed without a slash is part of the value, which is what makes it possible to search for somebody
 * named that.
 *
 * A command whose values are a fixed set still offers them (#239) — it is the **text** that says one
 * is open now, not a pinned chip, so `/file_type ` typed by hand opens the same list as picking it.
 * What goes into the box is the label; the value that travels is resolved on the way out.
 *
 * **And a search is a second Enter** (#268). The first Enter closes what is typed into a chip and
 * searches nothing; Enter again, with the text empty, sends the chips. Composing a query — picking a
 * command, spelling its value, removing or toggling a chip — is somebody still deciding, and none of it
 * reaches the server. While the chips differ from what the results answer, the box says Enter is
 * pending. «Clear filters» goes back to the screen's default search in one click and searches it.
 *
 * Keyboard: the arrows move through the suggestions and Enter or Tab confirm; Enter with no list
 * open closes what is typed into chips, or searches the chips when nothing is typed; Escape closes the list without taking down the
 * dialog hosting it; Backspace on empty text hands the last chip back to the cursor as text, ready to
 * be edited.
 */
export function SearchQueryInput({
  fields,
  value,
  onChange,
  searchedQuery,
  onSearch,
  placeholder,
  label,
  autoFocus,
  defaultQuery = '',
  extraFiltersActive = false,
  onClearExtraFilters,
}: SearchQueryInputProps) {
  const { t } = useTranslation('common')
  const inputRef = useRef<HTMLInputElement>(null)
  const listboxId = useId()
  const [highlightStep, setHighlightStep] = useState(0)

  const labelOf = (name: string | null) => fields.find((field) => field.name === name)?.label ?? name

  /**
   * How a criterion's values read on its chip, and how they read back in the box when it is edited.
   *
   * A field with fixed choices shows their labels and not the values that travel: nobody wants to
   * see `application/vnd.openxmlformats-officedocument.wordprocessingml.document` on a chip, and the
   * label is the same word they picked from the list — and the same word they would type.
   */
  const valuesOf = (field: string | null, values: readonly string[]) => {
    const choices = fields.find((candidate) => candidate.name === field)?.values
    if (choices === undefined) {
      return values.join(', ')
    }
    return values.map((raw) => choices.find((choice) => choice.value === raw)?.label ?? raw).join(', ')
  }
  const connectorLabel = (connector: SearchConnector) => t(connector === 'or' ? 'search.connectorOr' : 'search.connectorAnd')

  /** What is typed, minus the `/command` still being spelled at the end of it. */
  const rest = value.draft.replace(OPEN_FIELD_PREFIX, '')
  const openPrefix = OPEN_FIELD_PREFIX.exec(value.draft)?.[2] ?? null
  const openValue = openPrefix === null ? openValueOf(value.draft, fields) : null
  /**
   * The connector the next criterion comes in with. The text wins over the chip: a `/or` written at
   * the head of the draft is the more recent thing that was said.
   */
  const draftConnector = leadingConnector(rest)
  const pendingConnector = draftConnector ?? value.pendingConnector
  /**
   * The connectors are only offered when there is something to join: joining nothing means nothing.
   * One criterion is enough, closed or open — a lone `/or` in the text is not one, which is why this
   * asks the parser rather than counting words.
   */
  const canJoin = value.terms.length > 0 || toTerms(rest, fields).length > 0

  const suggestions: Suggestion[] =
    openPrefix !== null ? buildCommandSuggestions(openPrefix) : openValue !== null ? buildValueSuggestions(openValue) : []
  const isChoosing = suggestions.length > 0
  /** Derived on render rather than stored: the list changes length while somebody types. */
  const highlighted = isChoosing ? ((highlightStep % suggestions.length) + suggestions.length) % suggestions.length : 0

  /**
   * A field's fixed values, narrowed by whatever has been typed of one.
   *
   * Nothing is offered once what is typed *is* one of them: there is nothing left to choose, and a
   * list repeating the word already in the box is noise. That holds whether the label got there by
   * being picked or by being typed, which is the point of #240.
   */
  function buildValueSuggestions(open: NonNullable<ReturnType<typeof openValueOf>>): Suggestion[] {
    const typed = open.typed.trim().toLowerCase()
    const choices = open.field.values ?? []
    if (choices.some((choice) => choice.label.toLowerCase() === typed)) return []
    return choices
      .filter((choice) => typed === '' || choice.label.toLowerCase().includes(typed))
      .map((choice) => ({ kind: 'value', name: choice.value, label: choice.label, start: open.start }))
  }

  function buildCommandSuggestions(prefix: string): Suggestion[] {
    const options: Suggestion[] = fields.map((field) => ({ kind: 'field', name: field.name, label: field.label }))
    if (canJoin) {
      options.push(
        { kind: 'connector', name: 'and', label: connectorLabel('and'), connector: 'and' },
        { kind: 'connector', name: 'or', label: connectorLabel('or'), connector: 'or' },
      )
    }
    return options.filter((option) => option.name.startsWith(prefix.toLowerCase()))
  }

  /** The box as the screen first shows it; compared canonically, so chip order and spelling agree. */
  const defaultValue = searchQueryOf(defaultQuery, fields)
  const defaultCanonical = buildSearchQuery(defaultValue)
  const canClear =
    extraFiltersActive || value.draft.trim() !== '' || buildSearchQuery(value) !== defaultCanonical || searchedQuery !== defaultCanonical
  /**
   * Chips on screen that the results do not answer yet, with nothing left to close: the second Enter
   * is what is missing. While text is still typed, Enter closes it first, so the cue waits for that.
   */
  const hasPendingSearch = buildSearchQuery(value) !== searchedQuery && value.draft.trim() === ''

  /** Sends the chips as the search: the second Enter, or its button. */
  function confirmSearch() {
    if (value.draft !== '') onChange({ ...value, draft: '' })
    onSearch(buildSearchQuery(value))
  }

  function clearFilters() {
    setHighlightStep(0)
    onChange(defaultValue)
    onSearch(defaultCanonical)
    onClearExtraFilters?.()
    inputRef.current?.focus()
  }

  /** Writes a `/command` where the half-typed one was, with the cursor left on its value. */
  function withCommand(name: string): string {
    const head = rest.trimEnd()
    return head === '' ? `/${name} ` : `${head} /${name} `
  }

  function handleDraftChange(draft: string) {
    setHighlightStep(0)
    onChange({ ...value, draft })
  }

  function chooseSuggestion(suggestion: Suggestion) {
    // Picking only writes text — the same text typing it by hand would produce (#240).
    const draft = suggestion.kind === 'value' ? value.draft.slice(0, suggestion.start) + suggestion.label : withCommand(suggestion.name)
    setHighlightStep(0)
    onChange({ ...value, draft })
    inputRef.current?.focus()
  }

  function removeTerm(index: number) {
    onChange({ ...value, terms: value.terms.filter((_, position) => position !== index) })
  }

  function toggleConnector(index: number) {
    onChange({
      ...value,
      terms: value.terms.map((term, position) =>
        position === index ? { ...term, connector: term.connector === 'and' ? 'or' : 'and' } : term,
      ),
    })
  }

  /** The waiting connector is changed where it is written: in the text if it is there, in the state otherwise. */
  function togglePendingConnector() {
    const next: SearchConnector = pendingConnector === 'and' ? 'or' : 'and'
    if (draftConnector !== null) {
      onChange({ ...value, draft: value.draft.trimStart().replace(/^\/(and|or)/i, `/${next}`) })
      return
    }
    onChange({ ...value, pendingConnector: next })
  }

  /** Hands the last chip back to the cursor, as the text that would produce it again. */
  function editLastTerm() {
    const last = value.terms.at(-1)
    if (!last) return
    onChange({ terms: value.terms.slice(0, -1), draft: textOf(last), pendingConnector: last.connector })
  }

  function textOf(term: SearchTerm): string {
    const values = valuesOf(term.field, term.values)
    return term.field ? `/${term.field} ${values}` : values
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (isChoosing) {
      if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
        event.preventDefault()
        setHighlightStep((current) => current + (event.key === 'ArrowDown' ? 1 : -1))
        return
      }
      if (event.key === 'Enter' || event.key === 'Tab') {
        event.preventDefault()
        const chosen = suggestions[highlighted] ?? suggestions[0]
        if (chosen) chooseSuggestion(chosen)
        return
      }
      if (event.key === 'Escape') {
        // It only closes the list: Escape must not take down the dialog hosting it.
        event.preventDefault()
        event.stopPropagation()
        onChange({ ...value, draft: rest })
        return
      }
    }
    if (event.key === 'Enter') {
      event.preventDefault()
      // First Enter closes the text into chips; Enter on empty text is the one that searches (#268).
      if (value.draft.trim() !== '') {
        onChange(commitSearchDraft(value, fields))
        return
      }
      confirmSearch()
      return
    }
    if (event.key === 'Backspace' && value.draft === '') {
      event.preventDefault()
      editLastTerm()
    }
  }

  /**
   * The connector waiting to join whatever comes next.
   *
   * **Shown as soon as there is a criterion to join it to**, and not once somebody starts typing
   * again. Waiting made choosing `/and` look like it had done nothing - the chip appeared later, out
   * of nowhere - and it made `or` behave differently from `and` for no reason a reader could see.
   * Showing it says what the next criterion will be joined with, and the chip is how that is changed.
   */
  const showPendingConnector = value.terms.length > 0

  return (
    <div className="space-y-1">
      <div
        className={cn(
          'border-input flex flex-wrap items-center gap-1.5 rounded-md border px-2 py-1.5',
          'focus-within:border-ring focus-within:ring-ring/50 focus-within:ring-[3px]',
          // A search waiting for its Enter marks the whole box, not only a line under it (#268).
          hasPendingSearch && 'border-brand-500 focus-within:border-brand-500',
        )}
      >
        {value.terms.map((term, index) => (
          <Fragment key={`${term.field ?? ''}-${term.values.join(',')}-${index}`}>
            {index > 0 && (
              <ConnectorChip
                label={connectorLabel(term.connector)}
                title={t('search.toggleConnector')}
                onToggle={() => toggleConnector(index)}
              />
            )}
            <Chip
              label={labelOf(term.field)}
              text={valuesOf(term.field, term.values)}
              onRemove={() => removeTerm(index)}
              removeLabel={`${t('search.removeTerm')}: ${valuesOf(term.field, term.values)}`}
            />
          </Fragment>
        ))}
        {showPendingConnector && (
          <ConnectorChip label={connectorLabel(pendingConnector)} title={t('search.toggleConnector')} onToggle={togglePendingConnector} />
        )}
        <input
          ref={inputRef}
          type="text"
          role="combobox"
          aria-label={label}
          aria-expanded={isChoosing}
          aria-controls={listboxId}
          aria-autocomplete="list"
          aria-activedescendant={isChoosing ? `${listboxId}-${highlighted}` : undefined}
          autoFocus={autoFocus}
          value={value.draft}
          placeholder={value.terms.length > 0 ? undefined : placeholder}
          onChange={(event) => handleDraftChange(event.target.value)}
          onKeyDown={handleKeyDown}
          className="placeholder:text-fg-subtle min-w-32 flex-1 bg-transparent px-1 py-0.5 text-sm outline-none"
        />
        {/* The second Enter, made visible: it says what is missing and does it for a mouse or a phone. */}
        {hasPendingSearch && (
          <Button type="button" size="xs" onClick={confirmSearch} title={t('search.pendingSearch')}>
            <CornerDownLeft aria-hidden />
            {t('search.confirm')}
          </Button>
        )}
      </div>
      {isChoosing && (
        <ul
          id={listboxId}
          role="listbox"
          aria-label={label}
          className="border-border bg-raised divide-border divide-y rounded-md border text-sm"
        >
          {suggestions.map((suggestion, index) => (
            <li
              key={suggestion.name}
              id={`${listboxId}-${index}`}
              role="option"
              aria-selected={index === highlighted}
              onMouseEnter={() => setHighlightStep(index)}
              // onMouseDown and not onClick: a click starts by taking focus off the input, and the
              // list disappears with it before the event ever arrives.
              onMouseDown={(event) => {
                event.preventDefault()
                chooseSuggestion(suggestion)
              }}
              className={cn('flex cursor-pointer items-center gap-2 px-3 py-1.5', index === highlighted && 'bg-surface')}
            >
              {/* **The command first, its description after** (#239). What is being picked from this
                  list is the command, so it leads; the description explains it. It read the other way
                  round, with the command dimmed on the right, which is the wrong emphasis for the
                  thing somebody is here to learn.
                  A **value** has no command to show: `/application/pdf` next to "PDF" would teach
                  something that is not true, and it drags the MIME type into the accessible name. */}
              {suggestion.kind !== 'value' && <code className="text-fg text-xs font-medium">/{suggestion.name}</code>}
              <span className={cn('text-xs', suggestion.kind === 'value' ? 'text-fg text-sm' : 'text-fg-muted')}>
                {suggestion.kind === 'connector' ? t('search.joinWith', { connector: suggestion.label }) : suggestion.label}
              </span>
            </li>
          ))}
        </ul>
      )}
      <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-1">
        <p className="text-fg-subtle text-xs">
          {t('search.hint')}{' '}
          {/* The help is the box's own: it lists **these** commands, because a list of somebody else's
              is a list of things that do not work here (#240). */}
          <HelpLink section="basics.search" searchFields={fields}>
            {t('search.helpLink')}
          </HelpLink>
        </p>
        <div className="flex shrink-0 items-center gap-3">
          {/* Read out for screen readers; on screen, the box itself carries the cue. */}
          <p aria-live="polite" className="sr-only">
            {hasPendingSearch ? t('search.pendingSearch') : null}
          </p>
          {/* Only while there is something to clear: a button that does nothing is a question. */}
          {canClear && (
            <button
              type="button"
              onClick={clearFilters}
              className="text-fg-muted hover:text-fg flex shrink-0 items-center gap-1 text-xs underline-offset-2 hover:underline"
            >
              <FilterX className="size-3" aria-hidden />
              {t('search.clearFilters')}
            </button>
          )}
        </div>
      </div>
    </div>
  )
}

function ConnectorChip({ label, title, onToggle }: { label: string; title: string; onToggle: () => void }) {
  return (
    <button
      type="button"
      onClick={onToggle}
      title={title}
      className="border-border text-fg-muted hover:text-fg hover:bg-raised rounded-full border border-dashed px-2 py-0.5 text-xs uppercase"
    >
      {label}
    </button>
  )
}

interface ChipProps {
  /** The command's label, or null for the basic criterion, which has no command to name. */
  label: string | null
  text: string
  onRemove: () => void
  removeLabel: string
}

/** A closed criterion. It always carries its values: a chip with nothing in it is no longer a state (#240). */
function Chip({ label, text, onRemove, removeLabel }: ChipProps) {
  return (
    <span className="bg-raised text-fg flex items-center gap-1 rounded-full py-0.5 pr-1 pl-2 text-xs">
      {label && <span className="text-fg-muted">{label}:</span>}
      <span className="max-w-40 truncate">{text}</span>
      <button type="button" onClick={onRemove} aria-label={removeLabel} className="hover:text-fg-muted rounded-full">
        <X className="size-3" />
      </button>
    </span>
  )
}
