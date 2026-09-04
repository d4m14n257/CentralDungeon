import { describe, expect, it } from 'vitest'

import { buildSearchQuery, emptySearchQuery, parseSearchQuery, serializeSearchQuery, type SearchTerm } from './searchQuery'

const FIELDS = ['discord_name', 'user_name', 'tag']

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

describe('buildSearchQuery', () => {
  it('appends what is being typed behind its connector', () => {
    const terms: SearchTerm[] = [{ field: 'user_name', values: ['juan'], connector: 'and' }]

    expect(buildSearchQuery({ terms, activeField: null, draft: 'pab', pendingConnector: 'or' })).toBe('/user_name juan /or pab')
  })

  it('the open criterion searches by its field, not as loose text', () => {
    expect(buildSearchQuery({ ...emptySearchQuery, activeField: 'discord_name', draft: 'pab' })).toBe('/discord_name pab')
  })

  it('the commas of what is being typed are already alternatives', () => {
    expect(buildSearchQuery({ ...emptySearchQuery, activeField: 'user_name', draft: 'damian,carlos' })).toBe('/user_name damian,carlos')
  })

  it('a half-typed field searches for nothing', () => {
    expect(buildSearchQuery({ ...emptySearchQuery, draft: '/dis' })).toBe('')
    expect(buildSearchQuery({ ...emptySearchQuery, draft: 'juan /dis' })).toBe('juan')
  })

  it('an open field with no value searches for nothing', () => {
    expect(buildSearchQuery({ ...emptySearchQuery, activeField: 'discord_name', draft: '  ' })).toBe('')
  })

  it('with no draft, the query is only the chips', () => {
    const terms: SearchTerm[] = [{ field: 'tag', values: ['terror'], connector: 'and' }]

    expect(buildSearchQuery({ terms, activeField: null, draft: '  ', pendingConnector: 'and' })).toBe('/tag terror')
  })
})
