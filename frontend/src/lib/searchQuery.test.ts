import { describe, expect, it } from 'vitest'

import {
  buildSearchQuery,
  emptySearchQuery,
  leadingConnector,
  openValueOf,
  parseSearchQuery,
  searchQueryOf,
  serializeSearchQuery,
  toTerms,
  type SearchField,
  type SearchTerm,
} from './searchQuery'

const FIELDS = ['discord_name', 'user_name', 'tag']

/** The same three, plus one with fixed choices: the two kinds of command of #164. */
const SEARCH_FIELDS: SearchField[] = [
  { name: 'discord_name', label: 'Discord' },
  { name: 'user_name', label: 'Nombre' },
  { name: 'tag', label: 'Etiqueta' },
  {
    name: 'file_type',
    label: 'Tipo',
    values: [
      { value: 'application/pdf', label: 'PDF' },
      { value: 'image/png', label: 'PNG' },
    ],
  },
]

/** The same cases as the backend's SearchQueryParserTest: if they diverge, one of the two is wrong. */
describe('parseSearchQuery', () => {
  it('leaves loose text as a criterion with no field', () => {
    expect(parseSearchQuery('juan', FIELDS)).toEqual<SearchTerm[]>([{ field: null, values: ['juan'], connector: 'and' }])
  })

  it('everything after a field is its value, spaces included', () => {
    expect(parseSearchQuery('/user_name juan pablo', FIELDS)).toEqual<SearchTerm[]>([
      { field: 'user_name', values: ['juan pablo'], connector: 'and' },
    ])
  })

  it('commas separate alternatives of the same criterion', () => {
    expect(parseSearchQuery('/user_name damian,carlos, daniel', FIELDS)).toEqual<SearchTerm[]>([
      { field: 'user_name', values: ['damian', 'carlos', 'daniel'], connector: 'and' },
    ])
  })

  it('drops the gaps between commas', () => {
    expect(parseSearchQuery('damian, ,carlos,', FIELDS)).toEqual<SearchTerm[]>([
      { field: null, values: ['damian', 'carlos'], connector: 'and' },
    ])
  })

  it('joins two criteria with the connector that was written', () => {
    expect(parseSearchQuery('/user_name juan /or /discord_name pablo', FIELDS)).toEqual<SearchTerm[]>([
      { field: 'user_name', values: ['juan'], connector: 'and' },
      { field: 'discord_name', values: ['pablo'], connector: 'or' },
    ])
  })

  it('uses and when no connector is written', () => {
    expect(parseSearchQuery('/user_name juan /discord_name pablo', FIELDS).map((term) => term.connector)).toEqual(['and', 'and'])
  })

  it('reads the connectors regardless of case', () => {
    expect(parseSearchQuery('juan /OR pablo', FIELDS).map((term) => term.connector)).toEqual(['and', 'or'])
  })

  /** Without this nobody could search for a value containing the word: the separator is the slash. */
  it('a bare and or or is text', () => {
    expect(parseSearchQuery('/user_name juan or pablo', FIELDS)).toEqual<SearchTerm[]>([
      { field: 'user_name', values: ['juan or pablo'], connector: 'and' },
    ])
  })

  it('drops a trailing connector', () => {
    expect(parseSearchQuery('juan /or', FIELDS)).toEqual<SearchTerm[]>([{ field: null, values: ['juan'], connector: 'and' }])
  })

  it('ignores a field that has no value yet', () => {
    expect(parseSearchQuery('/user_name', FIELDS)).toEqual([])
    expect(parseSearchQuery('/user_name ,,', FIELDS)).toEqual([])
  })

  it('leaves an unknown prefix as literal text', () => {
    expect(parseSearchQuery('/nickname juan', FIELDS)).toEqual<SearchTerm[]>([
      { field: null, values: ['/nickname juan'], connector: 'and' },
    ])
  })

  it('a leading connector joins nothing and is harmless', () => {
    expect(parseSearchQuery('/or juan', FIELDS)).toEqual<SearchTerm[]>([{ field: null, values: ['juan'], connector: 'and' }])
  })

  it('returns empty for a blank query', () => {
    expect(parseSearchQuery('   ', FIELDS)).toEqual([])
  })
})

describe('serializeSearchQuery', () => {
  it('always writes the connector explicitly, with its slash', () => {
    const terms: SearchTerm[] = [
      { field: 'user_name', values: ['juan'], connector: 'and' },
      { field: 'discord_name', values: ['pablo', 'pedro'], connector: 'or' },
    ]

    expect(serializeSearchQuery(terms)).toBe('/user_name juan /or /discord_name pablo,pedro')
  })

  it('round trip: what was serialized parses back the same', () => {
    const raw = '/user_name juan,ana /or /discord_name pablo /and mesa'
    const terms = parseSearchQuery(raw, FIELDS)

    expect(parseSearchQuery(serializeSearchQuery(terms), FIELDS)).toEqual(terms)
  })
})

/**
 * The layer above the parser: the one that knows a command's fixed choices (#240).
 *
 * What is typed - or picked - is the label, and what leaves is the value that travels. Nothing else
 * is touched, which is what lets a canonical query read straight back from the URL.
 */
describe('toTerms', () => {
  it('resolves the label of a command with fixed choices into the value that travels', () => {
    expect(toTerms('/file_type PDF', SEARCH_FIELDS)).toEqual<SearchTerm[]>([
      { field: 'file_type', values: ['application/pdf'], connector: 'and' },
    ])
  })

  it('resolves each alternative of the same criterion', () => {
    expect(toTerms('/file_type PDF,PNG', SEARCH_FIELDS)[0]!.values).toEqual(['application/pdf', 'image/png'])
  })

  it('matches the label whatever its case', () => {
    expect(toTerms('/file_type pdf', SEARCH_FIELDS)[0]!.values).toEqual(['application/pdf'])
  })

  /** What comes back from `?q=` is already canonical, and must survive the round trip untouched. */
  it('leaves a value that is already the canonical one alone', () => {
    expect(toTerms('/file_type application/pdf', SEARCH_FIELDS)[0]!.values).toEqual(['application/pdf'])
  })

  it('leaves anything that is no choice of that command as it was typed', () => {
    expect(toTerms('/file_type mp3', SEARCH_FIELDS)[0]!.values).toEqual(['mp3'])
  })

  it('touches nothing on a free-text command', () => {
    expect(toTerms('/user_name PDF', SEARCH_FIELDS)[0]!.values).toEqual(['PDF'])
  })
})

/** What tells the box a command with fixed choices is open, now that no chip does (#240). */
describe('openValueOf', () => {
  it('finds the command whose value is being typed, and what is typed of it', () => {
    expect(openValueOf('/file_type PD', SEARCH_FIELDS)).toMatchObject({ typed: 'PD' })
    expect(openValueOf('/file_type PD', SEARCH_FIELDS)?.field.name).toBe('file_type')
  })

  it('is open with nothing typed yet, as soon as there is a space after the command', () => {
    expect(openValueOf('/file_type ', SEARCH_FIELDS)?.typed).toBe('')
  })

  it('is not open while the command itself is still being spelled', () => {
    expect(openValueOf('/file_ty', SEARCH_FIELDS)).toBeNull()
  })

  it('narrows the alternative after the last comma, not the whole value', () => {
    expect(openValueOf('/file_type PDF, PN', SEARCH_FIELDS)?.typed).toBe('PN')
  })

  it('points at where the half-typed alternative starts, so picking replaces only it', () => {
    const open = openValueOf('/file_type PDF, PN', SEARCH_FIELDS)!

    expect('/file_type PDF, PN'.slice(0, open.start)).toBe('/file_type PDF, ')
  })

  it('says nothing about a free-text command: there is no list to offer', () => {
    expect(openValueOf('/user_name dam', SEARCH_FIELDS)).toBeNull()
  })

  it('says nothing once a new command has been opened after it', () => {
    expect(openValueOf('/file_type PDF /user_name dam', SEARCH_FIELDS)).toBeNull()
  })
})

describe('leadingConnector', () => {
  it('reads the connector a text opens with', () => {
    expect(leadingConnector('/or juan')).toBe('or')
    expect(leadingConnector('  /AND juan')).toBe('and')
  })

  it('is none when the text opens with anything else', () => {
    expect(leadingConnector('juan /or pablo')).toBeNull()
    expect(leadingConnector('')).toBeNull()
  })
})

describe('buildSearchQuery', () => {
  it('appends what is being typed behind its connector', () => {
    const terms: SearchTerm[] = [{ field: 'user_name', values: ['juan'], connector: 'and' }]

    expect(buildSearchQuery({ terms, draft: 'pab', pendingConnector: 'or' }, SEARCH_FIELDS)).toBe('/user_name juan /or pab')
  })

  it('what is being typed searches by its command, without waiting for Enter', () => {
    expect(buildSearchQuery({ ...emptySearchQuery, draft: '/discord_name pab' }, SEARCH_FIELDS)).toBe('/discord_name pab')
  })

  it('the commas of what is being typed are already alternatives', () => {
    expect(buildSearchQuery({ ...emptySearchQuery, draft: '/user_name damian,carlos' }, SEARCH_FIELDS)).toBe('/user_name damian,carlos')
  })

  it('a half-typed command searches for nothing', () => {
    expect(buildSearchQuery({ ...emptySearchQuery, draft: '/dis' }, SEARCH_FIELDS)).toBe('')
    expect(buildSearchQuery({ ...emptySearchQuery, draft: 'juan /dis' }, SEARCH_FIELDS)).toBe('juan')
  })

  it('a command with no value searches for nothing', () => {
    expect(buildSearchQuery({ ...emptySearchQuery, draft: '/discord_name   ' }, SEARCH_FIELDS)).toBe('')
  })

  it('with no draft, the query is only the chips', () => {
    const terms: SearchTerm[] = [{ field: 'tag', values: ['terror'], connector: 'and' }]

    expect(buildSearchQuery({ terms, draft: '  ', pendingConnector: 'and' }, SEARCH_FIELDS)).toBe('/tag terror')
  })

  /**
   * The parser drops a leading connector because nothing is to its left *inside the text* — but there
   * are chips to its left on the screen, and that is what it was written to join.
   */
  it('a connector at the head of what is being typed joins it to the chips', () => {
    const terms: SearchTerm[] = [{ field: 'user_name', values: ['juan'], connector: 'and' }]

    expect(buildSearchQuery({ terms, draft: '/or pablo', pendingConnector: 'and' }, SEARCH_FIELDS)).toBe('/user_name juan /or pablo')
  })

  it('sends the value that travels, not the label that was typed', () => {
    expect(buildSearchQuery({ ...emptySearchQuery, draft: '/file_type PDF' }, SEARCH_FIELDS)).toBe('/file_type application/pdf')
  })
})

describe('searchQueryOf', () => {
  it('restores a canonical query as closed criteria, with nothing half-typed', () => {
    expect(searchQueryOf('/user_name juan /or /tag terror', SEARCH_FIELDS)).toEqual({
      terms: [
        { field: 'user_name', values: ['juan'], connector: 'and' },
        { field: 'tag', values: ['terror'], connector: 'or' },
      ],
      draft: '',
      pendingConnector: 'and',
    })
  })

  /** `?q=` and the box have to mean the same thing, or a refresh changes the search (#185). */
  it('round trip: what a restored box builds is the query it was restored from', () => {
    const raw = '/user_name juan,ana /or /file_type application/pdf'

    expect(buildSearchQuery(searchQueryOf(raw, SEARCH_FIELDS), SEARCH_FIELDS)).toBe(raw)
  })
})
