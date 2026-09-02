/**
 * El lenguaje de una línea que hablan todos los buscadores de la app (decisiones.md #164).
 *
 *   juan                                    -> criterio básico del endpoint
 *   /discord_name juan                      -> ese campo
 *   /user_name juan or /discord_name pablo  -> dos criterios, unidos
 *
 * Es el espejo exacto de `common/search/SearchQueryParser.java`: el backend es el que decide qué
 * devuelve la consulta, y esta copia existe para pintar los chips mientras se escribe. Las reglas
 * están escritas una vez de cada lado a propósito — si una cambia, cambian las dos, y los tests de
 * los dos lados cubren los mismos casos.
 */

export type SearchConnector = 'and' | 'or'

export interface SearchTerm {
  /** null = criterio básico: lo que se escribe sin un `/campo` adelante. */
  field: string | null
  value: string
  /** Cómo se une al término anterior. En el primero siempre es 'and' y no significa nada. */
  connector: SearchConnector
}

/** Un campo que el buscador acepta, con la etiqueta que ve quien lo usa. */
export interface SearchField {
  name: string
  label: string
}

const CONNECTORS: SearchConnector[] = ['and', 'or']

function asConnector(token: string): SearchConnector | null {
  const lowered = token.toLowerCase()
  return CONNECTORS.find((connector) => connector === lowered) ?? null
}

function fieldOf(token: string, knownFields: readonly string[]): string | null {
  if (!token.startsWith('/') || token.length < 2) return null
  const candidate = token.slice(1).toLowerCase()
  return knownFields.includes(candidate) ? candidate : null
}

/**
 * Un `/token` que no sea un campo conocido queda como texto literal: un typo busca lo que se
 * escribió, no rompe la búsqueda. Un campo sin valor todavía no es un término.
 */
export function parseSearchQuery(raw: string, knownFields: readonly string[]): SearchTerm[] {
  const terms: SearchTerm[] = []
  let field: string | null = null
  let value: string[] = []
  let pendingConnector: SearchConnector = 'and'

  function flush() {
    if (value.length === 0) return
    terms.push({ field, value: value.join(' '), connector: terms.length === 0 ? 'and' : pendingConnector })
    field = null
    value = []
    pendingConnector = 'and'
  }

  for (const token of raw.trim().split(/\s+/).filter(Boolean)) {
    const connector = asConnector(token)
    const knownField = fieldOf(token, knownFields)
    if (connector && (value.length > 0 || terms.length > 0)) {
      flush()
      pendingConnector = connector
      field = null
    } else if (knownField) {
      flush()
      field = knownField
    } else {
      value.push(token)
    }
  }
  flush()
  return terms
}

/** Devuelve la consulta canónica: siempre con el conector explícito, para que el backend la lea igual. */
export function serializeSearchQuery(terms: readonly SearchTerm[]): string {
  return terms
    .map((term, index) => {
      const clause = term.field ? `/${term.field} ${term.value}` : term.value
      return index === 0 ? clause : `${term.connector} ${clause}`
    })
    .join(' ')
}

/**
 * El estado de un buscador: los términos ya convertidos en chip, lo que se está escribiendo, y el
 * conector con el que va a entrar el próximo chip.
 */
export interface SearchQueryValue {
  terms: SearchTerm[]
  draft: string
  pendingConnector: SearchConnector
}

export const emptySearchQuery: SearchQueryValue = { terms: [], draft: '', pendingConnector: 'and' }

/** Lo que se escribió y todavía no se convirtió en chip se manda tal cual, detrás de su conector. */
export function buildSearchQuery({ terms, draft, pendingConnector }: SearchQueryValue): string {
  const committed = serializeSearchQuery(terms)
  const trimmedDraft = draft.trim()
  if (!trimmedDraft) return committed
  if (!committed) return trimmedDraft
  return `${committed} ${pendingConnector} ${trimmedDraft}`
}
