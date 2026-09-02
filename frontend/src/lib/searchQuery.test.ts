import { describe, expect, it } from 'vitest'

import { buildSearchQuery, emptySearchQuery, parseSearchQuery, serializeSearchQuery, type SearchTerm } from './searchQuery'

const FIELDS = ['discord_name', 'user_name', 'tag']

/** Los mismos casos que SearchQueryParserTest del backend: si divergen, uno de los dos está mal. */
describe('parseSearchQuery', () => {
  it('deja el texto suelto como un término sin campo', () => {
    expect(parseSearchQuery('juan', FIELDS)).toEqual<SearchTerm[]>([{ field: null, value: 'juan', connector: 'and' }])
  })

  it('conserva los espacios dentro del valor', () => {
    expect(parseSearchQuery('/user_name juan pablo', FIELDS)).toEqual<SearchTerm[]>([
      { field: 'user_name', value: 'juan pablo', connector: 'and' },
    ])
  })

  it('une dos campos con el conector escrito', () => {
    expect(parseSearchQuery('/user_name juan or /discord_name pablo', FIELDS)).toEqual<SearchTerm[]>([
      { field: 'user_name', value: 'juan', connector: 'and' },
      { field: 'discord_name', value: 'pablo', connector: 'or' },
    ])
  })

  it('usa and cuando no se escribe conector', () => {
    expect(parseSearchQuery('/user_name juan /discord_name pablo', FIELDS).map((term) => term.connector)).toEqual(['and', 'and'])
  })

  it('lee los conectores sin importar mayúsculas', () => {
    expect(parseSearchQuery('juan OR pablo', FIELDS).map((term) => term.connector)).toEqual(['and', 'or'])
  })

  it('descarta un conector al final', () => {
    expect(parseSearchQuery('juan or', FIELDS)).toEqual<SearchTerm[]>([{ field: null, value: 'juan', connector: 'and' }])
  })

  it('ignora un campo sin valor todavía', () => {
    expect(parseSearchQuery('/user_name', FIELDS)).toEqual([])
  })

  it('deja un prefijo desconocido como texto literal', () => {
    expect(parseSearchQuery('/nickname juan', FIELDS)).toEqual<SearchTerm[]>([
      { field: null, value: '/nickname juan', connector: 'and' },
    ])
  })

  it('trata un conector al principio como texto', () => {
    expect(parseSearchQuery('or juan', FIELDS)).toEqual<SearchTerm[]>([{ field: null, value: 'or juan', connector: 'and' }])
  })

  it('devuelve vacío con una consulta en blanco', () => {
    expect(parseSearchQuery('   ', FIELDS)).toEqual([])
  })
})

describe('serializeSearchQuery', () => {
  it('escribe siempre el conector explícito', () => {
    const terms: SearchTerm[] = [
      { field: 'user_name', value: 'juan', connector: 'and' },
      { field: 'discord_name', value: 'pablo', connector: 'or' },
    ]

    expect(serializeSearchQuery(terms)).toBe('/user_name juan or /discord_name pablo')
  })

  it('ida y vuelta: lo serializado se vuelve a parsear igual', () => {
    const raw = '/user_name juan or /discord_name pablo and mesa'
    const terms = parseSearchQuery(raw, FIELDS)

    expect(parseSearchQuery(serializeSearchQuery(terms), FIELDS)).toEqual(terms)
  })
})

describe('buildSearchQuery', () => {
  it('agrega lo que se está escribiendo detrás de su conector', () => {
    const terms: SearchTerm[] = [{ field: 'user_name', value: 'juan', connector: 'and' }]

    expect(buildSearchQuery({ terms, draft: 'pab', pendingConnector: 'or' })).toBe('/user_name juan or pab')
  })

  it('sin chips, la consulta es lo que se está escribiendo', () => {
    expect(buildSearchQuery({ ...emptySearchQuery, draft: 'pab' })).toBe('pab')
  })

  it('sin borrador, la consulta son solo los chips', () => {
    const terms: SearchTerm[] = [{ field: 'tag', value: 'terror', connector: 'and' }]

    expect(buildSearchQuery({ terms, draft: '  ', pendingConnector: 'and' })).toBe('/tag terror')
  })
})
