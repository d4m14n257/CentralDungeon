/**
 * The one-line language every search box in the application speaks (decisiones.md #164).
 *
 *   juan                                     -> the endpoint's basic criterion
 *   /discord_name juan                       -> that field
 *   /user_name damian,carlos                 -> that field, either of the two values
 *   /user_name juan /or /discord_name pablo  -> two criteria, joined
 *
 * **The slash is the only separator**: everything after a `/field` is its value, spaces included,
 * up to the next `/`. A bare "and" or "or" is text — it has to be, or nobody could search for a
 * value containing those words.
 *
 * It is an exact mirror of `common/search/SearchQueryParser.java`: the backend is what decides what
 * a query returns, and this copy exists to draw the chips while somebody types. The rules are
 * written once on each side on purpose — change one and both change, and the tests on either side
 * cover the same cases.
 */

export type SearchConnector = 'and' | 'or'

/**
 * One criterion of a search box: a field, the values that satisfy it, and how it joins the one
 * before it. Mirror of the backend's `SearchTerm` - the two parsers cover the same cases and are
 * kept in step by their tests (#164).
 */
export interface SearchTerm {
  /** null = the basic criterion: whatever is typed without a `/field` in front of it. */
  field: string | null
  /** Alternatives within one criterion, typed comma-separated. Never empty. */
  values: string[]
  /** How it joins the previous term. On the first one it is always 'and' and means nothing. */
  connector: SearchConnector
}

/** A field the search box accepts, with the label whoever uses it reads. */
export interface SearchField {
  name: string
  label: string
}

/**
 * The state of a search box: the criteria already closed, the open field — the chip that stays put
 * while its value is typed — what is being typed, and the connector the next criterion will come in
 * with.
 */
export interface SearchQueryValue {
  terms: SearchTerm[]
  /** The chosen `/field`: everything typed is its value until a `/` is typed again. */
  activeField: string | null
  draft: string
  pendingConnector: SearchConnector
}

/** The value of an empty search box. Shared, since the shape is never mutated in place. */
export const emptySearchQuery: SearchQueryValue = { terms: [], activeField: null, draft: '', pendingConnector: 'and' }

/**
 * The half-typed `/something` at the end of the text: while it is there, a field is being chosen and
 * there is nothing to search with yet.
 */
export const OPEN_FIELD_PREFIX = /(^|\s)\/([\w-]*)$/

const CONNECTOR_TOKENS: Record<string, SearchConnector> = { '/and': 'and', '/or': 'or' }

/** Splits a criterion's alternatives and drops the gaps: `damian, ,carlos,` is two of them. */
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
 * A `/token` that is neither a known field nor a connector stays literal text: a typo searches for
 * what was typed rather than breaking the search. A field with no value is not a criterion yet.
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

/** The canonical query: always with the connector written out, so the backend reads it the same way. */
export function serializeSearchQuery(terms: readonly SearchTerm[]): string {
  return terms
    .map((term, index) => {
      const values = term.values.join(',')
      const criterion = term.field ? `/${term.field} ${values}` : values
      return index === 0 ? criterion : `/${term.connector} ${criterion}`
    })
    .join(' ')
}

/** The open criterion is searched too: it travels as one more term, behind its connector. */
export function buildSearchQuery({ terms, activeField, draft, pendingConnector }: SearchQueryValue): string {
  const values = splitValues(draft.replace(OPEN_FIELD_PREFIX, ''))
  const open: SearchTerm[] = values.length > 0 ? [{ field: activeField, values, connector: pendingConnector }] : []
  return serializeSearchQuery([...terms, ...open])
}
