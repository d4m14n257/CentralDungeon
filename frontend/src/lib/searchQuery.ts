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
 * **And the text is all there is until Enter** (#240): picking a command from the list writes the
 * same string spelling it by hand would, so one query cannot reach two states depending on how it
 * was entered. Enter is what turns the text into criteria.
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

/** One choice of a command that takes a fixed set of them: what travels, and what is read and typed. */
export interface SearchChoice {
  value: string
  label: string
}

/**
 * A field the search box accepts, with the label whoever uses it reads.
 *
 * **The name is the command somebody types and it is always English and prefixed with its entity**
 * (#164): `file_name`, `user_name`, `catalog_name`. Two screens both offering a bare `/name` would
 * mean the same command searched different things depending on where it was typed.
 */
export interface SearchField {
  name: string
  label: string
  /**
   * The values this field accepts, when it accepts a fixed set rather than free text.
   *
   * **Two kinds of command, and the box behaves differently for each.** A filename is typed, because
   * nobody could list every filename; a file's type is chosen, because there are four of them and
   * typing `application/vnd.openxmlformats-officedocument.wordprocessingml.document` is not a thing
   * anybody will do. Declaring the values here is what turns the second kind into a list.
   *
   * The `value` is what travels to the backend; the `label` is what is **typed, read and matched**
   * (#240). Nothing but the label ever reaches the text box: the MIME type is resolved on the way
   * out, by {@link toTerms}.
   */
  values?: readonly SearchChoice[]
  /**
   * Values worth showing in this command's worked examples, in the help (#240).
   *
   * Only for the free-text kind, and only the caller can supply them: `damian` is a good example of a
   * `/user_name` and a terrible one of a `/file_name`, and this module knows about neither. Give more
   * than one and the help can also show what the commas are for. A command with fixed choices needs
   * none — its own choices are the examples, and they are real ones.
   */
  examples?: readonly string[]
}

/**
 * The state of a search box: the criteria already closed into chips, what is being typed, and the
 * connector the next criterion will come in with.
 *
 * **Nothing but Enter turns text into a chip** (#240). There used to be a third piece here, an
 * `activeField`: choosing a command from the list pinned its chip immediately, while typing the very
 * same command by hand left it as text until Enter — the same input reaching two different states
 * depending on which way it was entered. Now both ways write the same text, and the text is the only
 * thing that is being edited.
 */
export interface SearchQueryValue {
  terms: SearchTerm[]
  draft: string
  pendingConnector: SearchConnector
}

/** The value of an empty search box. Shared, since the shape is never mutated in place. */
export const emptySearchQuery: SearchQueryValue = { terms: [], draft: '', pendingConnector: 'and' }

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

/**
 * The `/field` whose value is being typed at the end of the text, when it is one that takes a fixed
 * set of them — and which of its alternatives is half-written (#240).
 *
 * This is what replaces the old pinned chip as the trigger for the value list: **the text says which
 * command is open**, so typing `/file_type ` by hand offers the same list as picking it from the
 * suggestions. It looks at the last comma so that `PDF, PN` is narrowing the second alternative and
 * not searching for both of them as one string.
 *
 * `start` is where the half-written alternative begins, which is what a caller replaces when
 * somebody picks from the list.
 */
export function openValueOf(draft: string, fields: readonly SearchField[]): { field: SearchField; typed: string; start: number } | null {
  // A space after the command and no `/` since: with a `/` still being spelled it is the command
  // that is being chosen, not its value, and OPEN_FIELD_PREFIX owns that case.
  const match = /(?:^|\s)\/([\w-]+)\s+([^/]*)$/.exec(draft)
  if (!match) return null
  const field = fields.find((candidate) => candidate.name === match[1]!.toLowerCase() && candidate.values !== undefined)
  if (field === undefined) return null
  const typed = match[2]!.slice(match[2]!.lastIndexOf(',') + 1).trimStart()
  return { field, typed, start: draft.length - typed.length }
}

/** The connector a piece of text opens with, when it opens with one: `/or juan` joins with «o». */
export function leadingConnector(raw: string): SearchConnector | null {
  return connectorOf(raw.trim().split(/\s+/)[0] ?? '')
}

/**
 * The criteria a piece of text means, with each value already the one that travels (#240).
 *
 * {@link parseSearchQuery} is the mirror of the backend and knows only field *names*; this is the
 * layer above it, the one that knows a command's fixed choices. Somebody types — or picks — the
 * label «PDF», and what leaves here is `application/pdf`. Anything that is not a label of that
 * command is left exactly as typed, which is what lets a query read back from the URL, where the
 * values are already canonical.
 */
export function toTerms(raw: string, fields: readonly SearchField[]): SearchTerm[] {
  return parseSearchQuery(
    raw,
    fields.map((field) => field.name),
  ).map((term) => {
    const choices = fields.find((field) => field.name === term.field)?.values
    if (choices === undefined) return term
    return {
      ...term,
      values: term.values.map((typed) => choices.find((choice) => choice.label.toLowerCase() === typed.toLowerCase())?.value ?? typed),
    }
  })
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

/**
 * What is being typed is searched too, without waiting for Enter: it is parsed as one more stretch
 * of criteria and joined behind the pending connector.
 *
 * A half-typed `/comm` at the end is dropped rather than searched: a command nobody finished spelling
 * is not text somebody is looking for.
 */
export function buildSearchQuery({ terms, draft, pendingConnector }: SearchQueryValue, fields: readonly SearchField[]): string {
  const rest = draft.replace(OPEN_FIELD_PREFIX, '')
  // The draft's own `/or` wins over the chip: it is the more recent thing that was said, and the
  // parser drops it as leading when read on its own — there is nothing to its left *inside the text*,
  // but there are chips to its left on the screen.
  const connector = leadingConnector(rest) ?? pendingConnector
  const open = toTerms(rest, fields).map((term, index) => (index === 0 ? { ...term, connector } : term))
  return serializeSearchQuery([...terms, ...open])
}

/** An empty box holding the criteria a canonical query means: how a screen restores `?q=` (#185). */
export function searchQueryOf(raw: string, fields: readonly SearchField[]): SearchQueryValue {
  return { ...emptySearchQuery, terms: toTerms(raw, fields) }
}
