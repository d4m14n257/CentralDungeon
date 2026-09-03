/**
 * El lenguaje de una línea que hablan todos los buscadores de la app (decisiones.md #164).
 *
 *   juan                                     -> criterio básico del endpoint
 *   /discord_name juan                       -> ese campo
 *   /user_name damian,carlos                 -> ese campo, cualquiera de los dos valores
 *   /user_name juan /or /discord_name pablo  -> dos criterios, unidos
 *
 * **La barra es el único separador**: todo lo que sigue a un `/campo` es su valor, espacios
 * incluidos, hasta el próximo `/`. Un "and" o un "or" sueltos son texto — tienen que serlo, o
 * nadie podría buscar un valor que contenga esas palabras.
 *
 * Es el espejo exacto de `common/search/SearchQueryParser.java`: el backend es el que decide qué
 * devuelve la consulta, y esta copia existe para pintar los chips mientras se escribe. Las reglas
 * están escritas una vez de cada lado a propósito — si una cambia, cambian las dos, y los tests de
 * los dos lados cubren los mismos casos.
 */

export type SearchConnector = 'and' | 'or'

/**
 * One criterion of a search box: a field, the values that satisfy it, and how it joins the one
 * before it. Mirror of the backend's `SearchTerm` - the two parsers cover the same cases and are
 * kept in step by their tests (#164).
 */
export interface SearchTerm {
  /** null = criterio básico: lo que se escribe sin un `/campo` adelante. */
  field: string | null
  /** Alternativas del mismo criterio, separadas por coma al escribir. Nunca vacío. */
  values: string[]
  /** Cómo se une al término anterior. En el primero siempre es 'and' y no significa nada. */
  connector: SearchConnector
}

/** Un campo que el buscador acepta, con la etiqueta que ve quien lo usa. */
export interface SearchField {
  name: string
  label: string
}

/**
 * El estado de un buscador: los criterios ya cerrados, el campo abierto —el chip que se queda
 * fijo mientras se escribe su valor—, lo que se está escribiendo, y el conector con el que va a
 * entrar el próximo criterio.
 */
export interface SearchQueryValue {
  terms: SearchTerm[]
  /** El `/campo` elegido: todo lo que se escriba es su valor hasta escribir `/` de nuevo. */
  activeField: string | null
  draft: string
  pendingConnector: SearchConnector
}

/** The value of an empty search box. Shared, since the shape is never mutated in place. */
export const emptySearchQuery: SearchQueryValue = { terms: [], activeField: null, draft: '', pendingConnector: 'and' }

/**
 * El `/algo` a medio escribir al final del texto: mientras exista, se está eligiendo un campo y
 * todavía no hay nada que buscar con eso.
 */
export const OPEN_FIELD_PREFIX = /(^|\s)\/([\w-]*)$/

const CONNECTOR_TOKENS: Record<string, SearchConnector> = { '/and': 'and', '/or': 'or' }

/** Separa las alternativas de un criterio y descarta los huecos: `damian, ,carlos,` son dos. */
export function splitValues(raw: string): string[] {
  return raw
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean)
}

function connectorOf(token: string): SearchConnector | null {
  return CONNECTOR_TOKENS[token.toLowerCase()] ?? null
}

function fieldOf(token: string, knownFields: readonly string[]): string | null {
  if (!token.startsWith('/') || token.length < 2) return null
  const candidate = token.slice(1).toLowerCase()
  return knownFields.includes(candidate) ? candidate : null
}

/**
 * Un `/token` que no sea un campo conocido ni un conector queda como texto literal: un typo busca
 * lo que se escribió, no rompe la búsqueda. Un campo sin valor todavía no es un criterio.
 */
export function parseSearchQuery(raw: string, knownFields: readonly string[]): SearchTerm[] {
  const terms: SearchTerm[] = []
  let field: string | null = null
  let buffer: string[] = []
  let pendingConnector: SearchConnector = 'and'

  function flush() {
    const values = splitValues(buffer.join(' '))
    buffer = []
    if (values.length === 0) {
      field = null
      return
    }
    terms.push({ field, values, connector: terms.length === 0 ? 'and' : pendingConnector })
    field = null
    pendingConnector = 'and'
  }

  for (const token of raw.trim().split(/\s+/).filter(Boolean)) {
    const connector = connectorOf(token)
    const knownField = fieldOf(token, knownFields)
    if (connector) {
      flush()
      pendingConnector = connector
      field = null
    } else if (knownField) {
      flush()
      field = knownField
    } else {
      buffer.push(token)
    }
  }
  flush()
  return terms
}

/** Devuelve la consulta canónica: siempre con el conector explícito, para que el backend la lea igual. */
export function serializeSearchQuery(terms: readonly SearchTerm[]): string {
  return terms
    .map((term, index) => {
      const values = term.values.join(',')
      const criterion = term.field ? `/${term.field} ${values}` : values
      return index === 0 ? criterion : `/${term.connector} ${criterion}`
    })
    .join(' ')
}

/** El criterio abierto también se busca: se manda como un término más, detrás de su conector. */
export function buildSearchQuery({ terms, activeField, draft, pendingConnector }: SearchQueryValue): string {
  const values = splitValues(draft.replace(OPEN_FIELD_PREFIX, ''))
  const open: SearchTerm[] = values.length > 0 ? [{ field: activeField, values, connector: pendingConnector }] : []
  return serializeSearchQuery([...terms, ...open])
}
