import { describe, expect, it } from 'vitest'

import i18n from '@/providers/i18n'
import { buildSearchQuery, searchQueryOf } from '@/lib/searchQuery'

import { ALL_TABLE_STATUSES } from './lifecycle'
import { adminTableSearchFields, explorerSearchFields } from './searchFields'

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

const tAdmin = i18n.getFixedT('es', 'admin')
const tTables = i18n.getFixedT('es', 'tables')

/**
 * The box `/admin/tables` grew when the screen stopped being a queue and started showing every table
 * there is (#176). A listing of everything with no way to narrow it is a listing nobody can use, so
 * the two things arrived in the same slice.
 */
describe('adminTableSearchFields', () => {
  it('offers the explorer four plus the two the admin listing needs', () => {
    expect(adminTableSearchFields(tAdmin, tTables).map((field) => field.name)).toEqual([
      'table_name',
      'table_status',
      'table_master',
      'table_system',
      'table_tag',
      'table_platform',
    ])
  })

  /**
   * #246 in both directions, in one screen: the status is a closed set of ten, so it is **chosen**;
   * the catalogs grow whenever a master proposes one (#55), so they are **typed**. Getting this
   * backwards either makes somebody guess a spelling or silently refuses a tag that exists.
   */
  it('declares fixed choices for the status and free text for everything else', () => {
    const byName = Object.fromEntries(adminTableSearchFields(tAdmin, tTables).map((field) => [field.name, field]))

    expect(byName.table_status?.values?.map((choice) => choice.value)).toEqual([...ALL_TABLE_STATUSES])
    expect(byName.table_master?.values).toBeUndefined()
    expect(byName.table_system?.values).toBeUndefined()
    expect(byName.table_tag?.values).toBeUndefined()
  })

  /** The value is what travels to the backend; the label is what is typed and read (#240). */
  it('sends the API spelling of a status and shows the translated label', () => {
    const status = adminTableSearchFields(tAdmin, tTables).find((field) => field.name === 'table_status')

    expect(status?.values).toContainEqual({ value: 'Preparation', label: 'En preparación' })
  })

  /**
   * A deleted table is gone for everybody (#175), so no row can be in that status and a command that
   * offered it would be a filter that always answers nothing.
   */
  it('offers no status a row cannot be in', () => {
    const values = adminTableSearchFields(tAdmin, tTables)
      .find((field) => field.name === 'table_status')
      ?.values?.map((choice) => choice.value)

    expect(values).not.toContain('Deleted')
  })

  /** Every free-text command carries worked examples, which is what the search help renders (#240). */
  it('gives every free-text command worked examples', () => {
    const free = adminTableSearchFields(tAdmin, tTables).filter((field) => field.values === undefined)

    expect(free.every((field) => (field.examples?.length ?? 0) > 0)).toBe(true)
  })

  /**
   * The `?q=` of #185 is read by two parsers, here and in `SearchQueryParser.java`. A status
   * criterion that does not survive the round trip does not fail loudly — it silently matches
   * nothing, and the reader sees an empty listing and concludes the platform has no tables.
   */
  it('round-trips a status criterion through the box in the wire spelling', () => {
    const fields = adminTableSearchFields(tAdmin, tTables)
    const value = searchQueryOf('/table_status Preparation', fields)

    expect(buildSearchQuery(value, fields)).toBe('/table_status Preparation')
  })

  /** A master is searched by name, never by id, like every other person command on the platform. */
  it('searches a master by name', () => {
    const fields = adminTableSearchFields(tAdmin, tTables)
    const value = searchQueryOf('/table_master damian', fields)

    expect(buildSearchQuery(value, fields)).toBe('/table_master damian')
  })
})
