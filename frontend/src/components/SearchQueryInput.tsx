import { Fragment, useId, useMemo, useRef, useState, type KeyboardEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { X } from 'lucide-react'

import { helpPath } from '@/config/paths'
import { cn } from '@/lib/utils'
import {
  OPEN_FIELD_PREFIX,
  parseSearchQuery,
  splitValues,
  type SearchConnector,
  type SearchField,
  type SearchQueryValue,
  type SearchTerm,
} from '@/lib/searchQuery'

interface SearchQueryInputProps {
  /** The fields this box accepts behind a `/`. Their order is the order of the suggestions. */
  fields: readonly SearchField[]
  value: SearchQueryValue
  onChange: (value: SearchQueryValue) => void
  placeholder?: string
  label: string
  autoFocus?: boolean
}

type Suggestion =
  { kind: 'field'; name: string; label: string } | { kind: 'connector'; name: string; label: string; connector: SearchConnector }

/**
 * The application's one search box (decisiones.md #164): it is typed on a single line, and each
 * criterion becomes a chip so that what is being searched, and by which field, stays in view.
 *
 * It knows no domain, on purpose — it receives the fields it accepts rather than knowing them
 * (arquitectura.md 3.1.1).
 *
 * **The slash is the only separator.** Choosing a field pins its chip to the left of the cursor and
 * **everything typed after it is that criterion's value**, spaces included; `/and` and `/or` close
 * the criterion and leave a chip of their own between the two, which can be tapped to change it.
 * Commas separate alternatives within one criterion. None of this uses bare reserved words: an "or"
 * typed without a slash is part of the value, which is what makes it possible to search for somebody
 * named that.
 *
 * Keyboard: the arrows move through the suggestions and Enter or Tab confirm; Enter with no list
 * open closes the criterion; Escape closes the list without taking down the dialog hosting it;
 * Backspace on empty text undoes backwards, first releasing the open field and then returning the
 * last chip to the input.
 */
export function SearchQueryInput({ fields, value, onChange, placeholder, label, autoFocus }: SearchQueryInputProps) {
  const { t } = useTranslation('common')
  const inputRef = useRef<HTMLInputElement>(null)
  const listboxId = useId()
  const [highlightStep, setHighlightStep] = useState(0)
  const fieldNames = useMemo(() => fields.map((field) => field.name), [fields])

  const labelOf = (name: string | null) => fields.find((field) => field.name === name)?.label ?? name
  const connectorLabel = (connector: SearchConnector) => t(connector === 'or' ? 'search.connectorOr' : 'search.connectorAnd')

  const openPrefix = OPEN_FIELD_PREFIX.exec(value.draft)?.[2] ?? null
  const hasOpenCriterion = value.activeField !== null || splitValues(value.draft.replace(OPEN_FIELD_PREFIX, '')).length > 0
  /**
   * The connectors are only offered when there is something to join: joining nothing means nothing.
   * One criterion is enough, closed or open — choosing the connector closes the open one.
   */
  const canJoin = value.terms.length > 0 || hasOpenCriterion

  const suggestions: Suggestion[] = openPrefix === null ? [] : buildSuggestions(openPrefix)
  const isChoosing = suggestions.length > 0
  /** Derived on render rather than stored: the list changes length while somebody types. */
  const highlighted = isChoosing ? ((highlightStep % suggestions.length) + suggestions.length) % suggestions.length : 0

  function buildSuggestions(prefix: string): Suggestion[] {
    const options: Suggestion[] = fields.map((field) => ({ kind: 'field', name: field.name, label: field.label }))
    if (canJoin) {
      options.push(
        { kind: 'connector', name: 'and', label: connectorLabel('and'), connector: 'and' },
        { kind: 'connector', name: 'or', label: connectorLabel('or'), connector: 'or' },
      )
    }
    return options.filter((option) => option.name.startsWith(prefix.toLowerCase()))
  }

  /**
   * Closes the open criterion and adds it as a chip, if it has a value. With a field open, the text
   * is its whole value, exactly as typed; with no field open it is parsed, so that pasting or typing
   * `/field value` in one go ends in the same chip as picking it from the list.
   */
  function closeTerm(rawValue: string, nextField: string | null, nextConnector: SearchConnector): SearchQueryValue {
    const closed: SearchTerm[] =
      splitValues(rawValue).length === 0
        ? []
        : value.activeField
          ? [{ field: value.activeField, values: splitValues(rawValue), connector: value.pendingConnector }]
          : parseSearchQuery(rawValue, fieldNames).map((term, index) =>
              index === 0 ? { ...term, connector: value.pendingConnector } : term,
            )
    if (closed.length === 0) {
      return { ...value, activeField: nextField, draft: '' }
    }
    return { terms: [...value.terms, ...closed], activeField: nextField, draft: '', pendingConnector: nextConnector }
  }

  function handleDraftChange(draft: string) {
    setHighlightStep(0)
    onChange({ ...value, draft })
  }

  function chooseSuggestion(suggestion: Suggestion) {
    const rest = value.draft.replace(OPEN_FIELD_PREFIX, '')
    if (suggestion.kind === 'field') {
      onChange(closeTerm(rest, suggestion.name, 'and'))
    } else {
      // Even with nothing to close, the chosen connector is kept: it is what was just asked for.
      onChange({ ...closeTerm(rest, null, suggestion.connector), pendingConnector: suggestion.connector })
    }
    inputRef.current?.focus()
  }

  function clearActiveField() {
    onChange({ ...value, activeField: null })
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

  function editLastTerm() {
    const last = value.terms.at(-1)
    if (!last) return
    onChange({
      terms: value.terms.slice(0, -1),
      activeField: last.field,
      draft: last.values.join(','),
      pendingConnector: last.connector,
    })
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
        onChange({ ...value, draft: value.draft.replace(OPEN_FIELD_PREFIX, '') })
        return
      }
    }
    if (event.key === 'Enter') {
      event.preventDefault()
      onChange(closeTerm(value.draft, null, 'and'))
      return
    }
    if (event.key === 'Backspace' && value.draft === '') {
      event.preventDefault()
      if (value.activeField) {
        clearActiveField()
        return
      }
      editLastTerm()
    }
  }

  const showPendingConnector = value.terms.length > 0 && (hasOpenCriterion || value.pendingConnector === 'or')

  return (
    <div className="space-y-1">
      <div
        className={cn(
          'border-input flex flex-wrap items-center gap-1.5 rounded-md border px-2 py-1.5',
          'focus-within:border-ring focus-within:ring-ring/50 focus-within:ring-[3px]',
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
              text={term.values.join(', ')}
              onRemove={() => removeTerm(index)}
              removeLabel={`${t('search.removeTerm')}: ${term.values.join(', ')}`}
            />
          </Fragment>
        ))}
        {showPendingConnector && (
          <ConnectorChip
            label={connectorLabel(value.pendingConnector)}
            title={t('search.toggleConnector')}
            onToggle={() => onChange({ ...value, pendingConnector: value.pendingConnector === 'and' ? 'or' : 'and' })}
          />
        )}
        {value.activeField && (
          <Chip
            label={labelOf(value.activeField)}
            onRemove={clearActiveField}
            removeLabel={`${t('search.removeField')}: ${labelOf(value.activeField) ?? ''}`}
          />
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
          placeholder={value.terms.length > 0 || value.activeField ? undefined : placeholder}
          onChange={(event) => handleDraftChange(event.target.value)}
          onKeyDown={handleKeyDown}
          className="placeholder:text-fg-subtle min-w-32 flex-1 bg-transparent px-1 py-0.5 text-sm outline-none"
        />
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
              className={cn('flex cursor-pointer items-center justify-between gap-2 px-3 py-1.5', index === highlighted && 'bg-surface')}
            >
              <span>{suggestion.kind === 'connector' ? t('search.joinWith', { connector: suggestion.label }) : suggestion.label}</span>
              <code className="text-fg-subtle text-xs">/{suggestion.name}</code>
            </li>
          ))}
        </ul>
      )}
      <p className="text-fg-subtle text-xs">
        {t('search.hint')}{' '}
        <Link to={helpPath(undefined, 'search')} className="underline underline-offset-2">
          {t('search.helpLink')}
        </Link>
      </p>
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
  label: string | null
  text?: string
  onRemove: () => void
  removeLabel: string
}

function Chip({ label, text, onRemove, removeLabel }: ChipProps) {
  return (
    <span
      className={cn('flex items-center gap-1 rounded-full py-0.5 pr-1 pl-2 text-xs', text ? 'bg-raised text-fg' : 'bg-primary/15 text-fg')}
    >
      {label && <span className="text-fg-muted">{label}:</span>}
      {text && <span className="max-w-40 truncate">{text}</span>}
      <button type="button" onClick={onRemove} aria-label={removeLabel} className="hover:text-fg-muted rounded-full">
        <X className="size-3" />
      </button>
    </span>
  )
}
