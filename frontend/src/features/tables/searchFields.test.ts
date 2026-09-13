import { describe, expect, it } from 'vitest'

import { buildSearchQuery, searchQueryOf } from '@/lib/searchQuery'

import { explorerSearchFields } from './searchFields'

/** The translator the box would pass in; the labels are not what these tests are about. */
const t = ((key: string) => key) as never

describe('explorerSearchFields', () => {
  it('offers the four commands the backend understands, entity first (#239)', () => {
    expect(explorerSearchFields(t).map((field) => field.name)).toEqual(['table_name', 'table_system', 'table_tag', 'table_platform'])
  })

  /**
   * The half of #246 that lives on this side. A command with `values` turns into a list the box
   * offers; a catalog has hundreds of them and grows whenever a master proposes one, so all four are
   * typed. If somebody ever adds `values` here, the box silently stops accepting anything that is
   * not on the list - which is a table nobody can find.
   */
  it('declares no fixed values: every catalog command is free text (#246)', () => {
    expect(explorerSearchFields(t).every((field) => field.values === undefined)).toBe(true)
  })

  it('gives every command worked examples for the help (#240)', () => {
    expect(explorerSearchFields(t).every((field) => (field.examples?.length ?? 0) > 0)).toBe(true)
  })

  it('round-trips a catalog criterion through the box and back to the wire', () => {
    const fields = explorerSearchFields(t)
    const value = searchQueryOf('/table_tag Principiantes', fields)

    expect(buildSearchQuery(value, fields)).toBe('/table_tag Principiantes')
  })

  /** A term typed with no command is the table's name, which is what the placeholder promises. */
  it('keeps a bare term as the default criterion', () => {
    const fields = explorerSearchFields(t)
    const value = searchQueryOf('strahd', fields)

    expect(buildSearchQuery(value, fields)).toBe('strahd')
  })
})
