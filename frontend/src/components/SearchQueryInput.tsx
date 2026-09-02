import { useId, useMemo, useRef, useState, type KeyboardEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { X } from 'lucide-react'

import { cn } from '@/lib/utils'
import {
  OPEN_FIELD_PREFIX,
  parseSearchQuery,
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

/** "juan or " / "juan and " — escribir el conector y un espacio cierra el criterio anterior. */
const TRAILING_CONNECTOR = /^(.*\S)\s+(and|or)\s+$/i

/**
 * El buscador de toda la app (decisiones.md #164): se escribe en una línea, y cada criterio se
 * vuelve un chip para que quede a la vista qué se está buscando y por qué campo.
 *
 * Sin dominio a propósito — recibe los campos que acepta, no los conoce (arquitectura.md 3.1.1).
 *
 * **El `/` es el separador.** Al elegir un campo, su chip queda fijo a la izquierda del cursor y
 * **todo lo que se escriba después es el valor de ese criterio**, espacios incluidos; escribir `/`
 * otra vez cierra el criterio y abre el siguiente. Sin eso no había forma de buscar un valor con
 * espacios sin que la segunda palabra pareciera otra cosa. Enter también cierra el criterio, y las
 * flechas eligen entre los campos sugeridos sin soltar el teclado (`role="combobox"` + `option`,
 * para que la lista exista también para un lector de pantalla). Backspace con el texto vacío
 * deshace hacia atrás: primero suelta el campo abierto, después devuelve el último chip al input.
 * Y el conector entre dos chips se toca para cambiarlo de "y" a "o", que es la operación que no se
 * descubre escribiendo.
 */
export function SearchQueryInput({ fields, value, onChange, placeholder, label, autoFocus }: SearchQueryInputProps) {
  const { t } = useTranslation('common')
  const inputRef = useRef<HTMLInputElement>(null)
  const listboxId = useId()
  const [highlightStep, setHighlightStep] = useState(0)
  const fieldNames = useMemo(() => fields.map((field) => field.name), [fields])

  const labelOf = (name: string | null) => fields.find((field) => field.name === name)?.label ?? name

  const openPrefix = OPEN_FIELD_PREFIX.exec(value.draft)?.[2] ?? null
  const suggestions = openPrefix === null ? [] : fields.filter((field) => field.name.startsWith(openPrefix.toLowerCase()))
  const isChoosingField = suggestions.length > 0
  /** Se deriva en el render en vez de guardarse: la lista cambia de largo mientras se escribe. */
  const highlighted = isChoosingField ? ((highlightStep % suggestions.length) + suggestions.length) % suggestions.length : 0

  /**
   * Cierra el criterio abierto y lo agrega como chip, si tiene valor. Con un campo abierto el
   * texto es su valor entero, tal cual; sin campo abierto se parsea, para que pegar o escribir
   * `/campo valor` de una sola vez llegue al mismo chip que elegirlo de la lista.
   */
  function closeTerm(rawValue: string, nextField: string | null, nextConnector: SearchConnector): SearchQueryValue {
    const trimmed = rawValue.trim()
    const closed: SearchTerm[] = !trimmed
      ? []
      : value.activeField
        ? [{ field: value.activeField, value: trimmed, connector: value.pendingConnector }]
        : parseSearchQuery(trimmed, fieldNames).map((term, index) =>
            index === 0 ? { ...term, connector: value.pendingConnector } : term,
          )
    if (closed.length === 0) {
      return { ...value, activeField: nextField, draft: '' }
    }
    return { terms: [...value.terms, ...closed], activeField: nextField, draft: '', pendingConnector: nextConnector }
  }

  function handleDraftChange(draft: string) {
    setHighlightStep(0)
    const trailing = TRAILING_CONNECTOR.exec(draft)
    if (trailing) {
      const [, head, connector] = trailing
      onChange(closeTerm(head ?? '', null, (connector ?? 'and').toLowerCase() as SearchConnector))
      return
    }
    onChange({ ...value, draft })
  }

  /** Elegir un campo cierra lo que se venía escribiendo y deja su chip abierto para el valor. */
  function selectField(field: SearchField) {
    onChange(closeTerm(value.draft.replace(OPEN_FIELD_PREFIX, ''), field.name, 'and'))
    inputRef.current?.focus()
  }

  function clearActiveField() {
    onChange({ ...value, activeField: null })
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
      activeField: last.field,
      draft: last.value,
      pendingConnector: last.connector,
    })
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (isChoosingField) {
      if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
        event.preventDefault()
        const step = event.key === 'ArrowDown' ? 1 : -1
        setHighlightStep((current) => current + step)
        return
      }
      if (event.key === 'Enter' || event.key === 'Tab') {
        event.preventDefault()
        const chosen = suggestions[highlighted] ?? suggestions[0]
        if (chosen) selectField(chosen)
        return
      }
      if (event.key === 'Escape') {
        // Solo cierra la lista: el Escape no tiene que llevarse por delante el diálogo que la aloja.
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
            label={labelOf(term.field)}
            text={term.value}
            showConnector={index > 0}
            connectorLabel={t(term.connector === 'or' ? 'search.connectorOr' : 'search.connectorAnd')}
            onToggleConnector={() => toggleConnector(index)}
            onRemove={() => removeTerm(index)}
            removeLabel={`${t('search.removeTerm')}: ${term.value}`}
          />
        ))}
        {value.activeField && (
          <TermChip
            label={labelOf(value.activeField)}
            showConnector={value.terms.length > 0}
            connectorLabel={t(value.pendingConnector === 'or' ? 'search.connectorOr' : 'search.connectorAnd')}
            onToggleConnector={() =>
              onChange({ ...value, pendingConnector: value.pendingConnector === 'and' ? 'or' : 'and' })
            }
            onRemove={clearActiveField}
            removeLabel={`${t('search.removeField')}: ${labelOf(value.activeField) ?? ''}`}
          />
        )}
        <input
          ref={inputRef}
          type="text"
          role="combobox"
          aria-label={label}
          aria-expanded={isChoosingField}
          aria-controls={listboxId}
          aria-autocomplete="list"
          aria-activedescendant={isChoosingField ? `${listboxId}-${highlighted}` : undefined}
          autoFocus={autoFocus}
          value={value.draft}
          placeholder={value.terms.length > 0 || value.activeField ? undefined : placeholder}
          onChange={(event) => handleDraftChange(event.target.value)}
          onKeyDown={handleKeyDown}
          className="placeholder:text-fg-subtle min-w-32 flex-1 bg-transparent px-1 py-0.5 text-sm outline-none"
        />
      </div>
      {isChoosingField && (
        <ul id={listboxId} role="listbox" aria-label={label} className="border-border bg-raised divide-border divide-y rounded-md border text-sm">
          {suggestions.map((field, index) => (
            <li
              key={field.name}
              id={`${listboxId}-${index}`}
              role="option"
              aria-selected={index === highlighted}
              onMouseEnter={() => setHighlightStep(index)}
              // onMouseDown y no onClick: el click empieza por quitarle el foco al input, y la
              // lista desaparece con él antes de que el evento llegue.
              onMouseDown={(event) => {
                event.preventDefault()
                selectField(field)
              }}
              className={cn(
                'flex cursor-pointer items-center justify-between gap-2 px-3 py-1.5',
                index === highlighted && 'bg-surface',
              )}
            >
              <span>{field.label}</span>
              <code className="text-fg-subtle text-xs">/{field.name}</code>
            </li>
          ))}
        </ul>
      )}
      <p className="text-fg-subtle text-xs">{t('search.hint')}</p>
    </div>
  )
}

interface TermChipProps {
  label: string | null
  text?: string
  showConnector: boolean
  connectorLabel: string
  onToggleConnector: () => void
  onRemove: () => void
  removeLabel: string
}

function TermChip({ label, text, showConnector, connectorLabel, onToggleConnector, onRemove, removeLabel }: TermChipProps) {
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
      <span className={cn('flex items-center gap-1 rounded-full py-0.5 pr-1 pl-2 text-xs', text ? 'bg-raised text-fg' : 'bg-primary/15 text-fg')}>
        {label && <span className="text-fg-muted">{label}:</span>}
        {text && <span className="max-w-40 truncate">{text}</span>}
        <button type="button" onClick={onRemove} aria-label={removeLabel} className="hover:text-fg-muted rounded-full">
          <X className="size-3" />
        </button>
      </span>
    </span>
  )
}
