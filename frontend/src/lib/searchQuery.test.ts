import { describe, expect, it } from 'vitest'

import { buildSearchQuery, emptySearchQuery, parseSearchQuery, serializeSearchQuery, type SearchTerm } from './searchQuery'

const FIELDS = ['discord_name', 'user_name', 'tag']

/** Los mismos casos que SearchQueryParserTest del backend: si divergen, uno de los dos está mal. */
describe('parseSearchQuery', () => {
  it('deja el texto suelto como un criterio sin campo', () => {
    expect(parseSearchQuery('juan', FIELDS)).toEqual<SearchTerm[]>([{ field: null, values: ['juan'], connector: 'and' }])
  })

  it('todo lo que sigue a un campo es su valor, espacios incluidos', () => {
    expect(parseSearchQuery('/user_name juan pablo', FIELDS)).toEqual<SearchTerm[]>([
      { field: 'user_name', values: ['juan pablo'], connector: 'and' },
    ])
  })

  it('las comas separan alternativas del mismo criterio', () => {
    expect(parseSearchQuery('/user_name damian,carlos, daniel', FIELDS)).toEqual<SearchTerm[]>([
      { field: 'user_name', values: ['damian', 'carlos', 'daniel'], connector: 'and' },
    ])
  })

  it('descarta los huecos entre comas', () => {
    expect(parseSearchQuery('damian, ,carlos,', FIELDS)).toEqual<SearchTerm[]>([
      { field: null, values: ['damian', 'carlos'], connector: 'and' },
    ])
  })

  it('une dos criterios con el conector escrito', () => {
    expect(parseSearchQuery('/user_name juan /or /discord_name pablo', FIELDS)).toEqual<SearchTerm[]>([
      { field: 'user_name', values: ['juan'], connector: 'and' },
      { field: 'discord_name', values: ['pablo'], connector: 'or' },
    ])
  })

  it('usa and cuando no se escribe conector', () => {
    expect(parseSearchQuery('/user_name juan /discord_name pablo', FIELDS).map((term) => term.connector)).toEqual(['and', 'and'])
  })

  it('lee los conectores sin importar mayúsculas', () => {
    expect(parseSearchQuery('juan /OR pablo', FIELDS).map((term) => term.connector)).toEqual(['and', 'or'])
  })

  /** Sin esto nadie podría buscar un valor que contenga la palabra: el separador es la barra. */
  it('un and o un or sueltos son texto', () => {
    expect(parseSearchQuery('/user_name juan or pablo', FIELDS)).toEqual<SearchTerm[]>([
      { field: 'user_name', values: ['juan or pablo'], connector: 'and' },
    ])
  })

  it('descarta un conector al final', () => {
    expect(parseSearchQuery('juan /or', FIELDS)).toEqual<SearchTerm[]>([{ field: null, values: ['juan'], connector: 'and' }])
  })

  it('ignora un campo sin valor todavía', () => {
    expect(parseSearchQuery('/user_name', FIELDS)).toEqual([])
    expect(parseSearchQuery('/user_name ,,', FIELDS)).toEqual([])
  })

  it('deja un prefijo desconocido como texto literal', () => {
    expect(parseSearchQuery('/nickname juan', FIELDS)).toEqual<SearchTerm[]>([
      { field: null, values: ['/nickname juan'], connector: 'and' },
    ])
  })

  it('un conector al principio no une nada y es inofensivo', () => {
    expect(parseSearchQuery('/or juan', FIELDS)).toEqual<SearchTerm[]>([{ field: null, values: ['juan'], connector: 'and' }])
  })

  it('devuelve vacío con una consulta en blanco', () => {
    expect(parseSearchQuery('   ', FIELDS)).toEqual([])
  })
})

describe('serializeSearchQuery', () => {
  it('escribe siempre el conector explícito, con barra', () => {
    const terms: SearchTerm[] = [
      { field: 'user_name', values: ['juan'], connector: 'and' },
      { field: 'discord_name', values: ['pablo', 'pedro'], connector: 'or' },
    ]

    expect(serializeSearchQuery(terms)).toBe('/user_name juan /or /discord_name pablo,pedro')
  })

  it('ida y vuelta: lo serializado se vuelve a parsear igual', () => {
    const raw = '/user_name juan,ana /or /discord_name pablo /and mesa'
    const terms = parseSearchQuery(raw, FIELDS)

    expect(parseSearchQuery(serializeSearchQuery(terms), FIELDS)).toEqual(terms)
  })
})

describe('buildSearchQuery', () => {
  it('agrega lo que se está escribiendo detrás de su conector', () => {
    const terms: SearchTerm[] = [{ field: 'user_name', values: ['juan'], connector: 'and' }]

    expect(buildSearchQuery({ terms, activeField: null, draft: 'pab', pendingConnector: 'or' })).toBe('/user_name juan /or pab')
  })

  it('el criterio abierto se busca por su campo, no como texto suelto', () => {
    expect(buildSearchQuery({ ...emptySearchQuery, activeField: 'discord_name', draft: 'pab' })).toBe('/discord_name pab')
  })

  it('las comas de lo que se está escribiendo ya son alternativas', () => {
    expect(buildSearchQuery({ ...emptySearchQuery, activeField: 'user_name', draft: 'damian,carlos' })).toBe(
      '/user_name damian,carlos',
    )
  })

  it('un campo a medio escribir no busca nada', () => {
    expect(buildSearchQuery({ ...emptySearchQuery, draft: '/dis' })).toBe('')
    expect(buildSearchQuery({ ...emptySearchQuery, draft: 'juan /dis' })).toBe('juan')
  })

  it('un campo abierto sin valor no busca nada', () => {
    expect(buildSearchQuery({ ...emptySearchQuery, activeField: 'discord_name', draft: '  ' })).toBe('')
  })

  it('sin borrador, la consulta son solo los chips', () => {
    const terms: SearchTerm[] = [{ field: 'tag', values: ['terror'], connector: 'and' }]

    expect(buildSearchQuery({ terms, activeField: null, draft: '  ', pendingConnector: 'and' })).toBe('/tag terror')
  })
})
