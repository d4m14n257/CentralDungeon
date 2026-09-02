import { useMemo, useRef, type KeyboardEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { X } from 'lucide-react'

import { cn } from '@/lib/utils'
import {
  parseSearchQuery,
  serializeSearchQuery,
  type SearchConnector,
  type SearchField,
  type SearchQueryValue,
  type SearchTerm,
} from '@/lib/searchQuery'

interface SearchQueryInputProps {
  /** Los campos que este buscador acepta detrás de un `/`. El orden es el de las sugerencias. */
  fields: readonly SearchField[]
  value: SearchQueryValue
  onChange: (value: SearchQueryValue) => void
  placeholder?: string
  label: string
  autoFocus?: boolean
}

/** "juan or " / "juan and " — escribir el conector y un espacio cierra el chip anterior. */
const TRAILING_CONNECTOR = /^(.*\S)\s+(and|or)\s+$/i

/**
 * El buscador de toda la app (decisiones.md #164): se escribe en una línea, y cada criterio
 * completo se vuelve un chip para que quede a la vista qué se está buscando y por qué campo.
 *
 * Sin dominio a propósito — recibe los campos que acepta, no los conoce (arquitectura.md 3.1.1).
 *
 * Teclado: Enter cierra el chip, Tab completa el `/campo` sugerido, Backspace con el texto vacío
 * devuelve el último chip al input para editarlo. Y el conector entre dos chips se toca para
 * cambiarlo de "y" a "o", que es la operación que no se descubre escribiendo.
 */
export function SearchQueryInput({ fields, value, onChange, placeholder, label, autoFocus }: SearchQueryInputProps) {
  const { t } = useTranslation('common')
  const inputRef = useRef<HTMLInputElement>(null)
  const fieldNames = useMemo(() => fields.map((field) => field.name), [fields])

  const labelOf = (name: string | null) => fields.find((field) => field.name === name)?.label ?? name

  /** El `/algo` a medio escribir al final del borrador: mientras exista, se ofrecen campos. */
  const openPrefix = /(^|\s)\/([\w-]*)$/.exec(value.draft)?.[2] ?? null
  const suggestions = openPrefix === null ? [] : fields.filter((field) => field.name.startsWith(openPrefix.toLowerCase()))

  function commit(rawDraft: string, nextConnector: SearchConnector) {
    const parsed = parseSearchQuery(rawDraft, fieldNames)
    const committed = parsed.map((term, index) => (index === 0 ? { ...term, connector: value.pendingConnector } : term))
    onChange({ terms: [...value.terms, ...committed], draft: '', pendingConnector: parsed.length > 0 ? nextConnector : value.pendingConnector })
  }

  function handleDraftChange(draft: string) {
    const trailing = TRAILING_CONNECTOR.exec(draft)
    if (trailing) {
      const [, head, connector] = trailing
      commit(head ?? '', (connector ?? 'and').toLowerCase() as SearchConnector)
      return
    }
    onChange({ ...value, draft })
  }

  function applySuggestion(field: SearchField) {
    onChange({ ...value, draft: value.draft.replace(/\/[\w-]*$/, `/${field.name} `) })
    inputRef.current?.focus()
  }

  function removeTerm(index: number) {
    onChange({ ...value, terms: value.terms.filter((_, position) => position !== index) })
  }

  /** El conector del primer chip no se muestra: no une nada. */
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
      draft: serializeSearchQuery([{ ...last, connector: 'and' }]),
      pendingConnector: last.connector,
    })
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    const [firstSuggestion] = suggestions
    if (event.key === 'Tab' && firstSuggestion) {
      event.preventDefault()
      applySuggestion(firstSuggestion)
      return
    }
    if (event.key === 'Enter') {
      event.preventDefault()
      if (firstSuggestion) {
        applySuggestion(firstSuggestion)
        return
      }
      commit(value.draft, 'and')
      return
    }
    if (event.key === 'Backspace' && value.draft === '') {
      event.preventDefault()
      editLastTerm()
    }
  }

  return (
    <div className="space-y-1">
      <div
        className={cn(
          'border-input flex flex-wrap items-center gap-1.5 rounded-md border px-2 py-1.5',
          'focus-within:border-ring focus-within:ring-ring/50 focus-within:ring-[3px]',
        )}
      >
        {value.terms.map((term, index) => (
          <TermChip
            key={`${term.field ?? ''}-${term.value}-${index}`}
            term={term}
            fieldLabel={labelOf(term.field)}
            showConnector={index > 0}
            connectorLabel={t(term.connector === 'or' ? 'search.connectorOr' : 'search.connectorAnd')}
            onToggleConnector={() => toggleConnector(index)}
            onRemove={() => removeTerm(index)}
            removeLabel={t('search.removeTerm')}
          />
        ))}
        <input
          ref={inputRef}
          type="text"
          aria-label={label}
          autoFocus={autoFocus}
          value={value.draft}
          placeholder={value.terms.length > 0 ? undefined : placeholder}
          onChange={(event) => handleDraftChange(event.target.value)}
          onKeyDown={handleKeyDown}
          className="placeholder:text-fg-subtle min-w-32 flex-1 bg-transparent px-1 py-0.5 text-sm outline-none"
        />
      </div>
      {suggestions.length > 0 && (
        <ul className="border-border bg-raised divide-border divide-y rounded-md border text-sm">
          {suggestions.map((field) => (
            <li key={field.name}>
              <button
                type="button"
                onClick={() => applySuggestion(field)}
                className="hover:bg-surface flex w-full items-center justify-between gap-2 px-3 py-1.5 text-left"
              >
                <span>{field.label}</span>
                <code className="text-fg-subtle text-xs">/{field.name}</code>
              </button>
            </li>
          ))}
        </ul>
      )}
      <p className="text-fg-subtle text-xs">{t('search.hint')}</p>
    </div>
  )
}

interface TermChipProps {
  term: SearchTerm
  fieldLabel: string | null
  showConnector: boolean
  connectorLabel: string
  onToggleConnector: () => void
  onRemove: () => void
  removeLabel: string
}

function TermChip({ term, fieldLabel, showConnector, connectorLabel, onToggleConnector, onRemove, removeLabel }: TermChipProps) {
  return (
    <span className="flex items-center gap-1.5">
      {showConnector && (
        <button
          type="button"
          onClick={onToggleConnector}
          className="text-fg-muted hover:text-fg hover:bg-raised rounded px-1 text-xs uppercase"
        >
          {connectorLabel}
        </button>
      )}
      <span className="bg-raised text-fg flex items-center gap-1 rounded-full py-0.5 pr-1 pl-2 text-xs">
        {fieldLabel && <span className="text-fg-muted">{fieldLabel}:</span>}
        <span className="max-w-40 truncate">{term.value}</span>
        <button type="button" onClick={onRemove} aria-label={`${removeLabel}: ${term.value}`} className="hover:text-fg-muted rounded-full">
          <X className="size-3" />
        </button>
      </span>
    </span>
  )
}
