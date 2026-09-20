import { describe, expect, it } from 'vitest'

import i18n from '@/providers/i18n'
import { buildSearchQuery, searchQueryOf } from '@/lib/searchQuery'

import { PENDING_REQUESTS_QUERY } from './requestTypes'
import { approvalRequestSearchFields } from './searchFields'

const t = i18n.getFixedT('es', 'admin')

describe('approvalRequestSearchFields', () => {
  it('offers the three commands of F3.2, in order', () => {
    expect(approvalRequestSearchFields(t).map((field) => field.name)).toEqual(['request_type', 'status', 'requested_by'])
  })

  /**
   * #240: a command whose values are a closed set declares them, and that is what turns it into a
   * list the box offers instead of something somebody has to guess the spelling of.
   */
  it('declares fixed choices for the two closed sets and free text for the name', () => {
    const byName = Object.fromEntries(approvalRequestSearchFields(t).map((field) => [field.name, field]))

    expect(byName.request_type?.values?.map((choice) => choice.value)).toEqual([
      'MasterGrant',
      'TableOpen',
      'General',
      'TablePause',
      'PlayerBan',
    ])
    expect(byName.status?.values?.map((choice) => choice.value)).toEqual(['Pending', 'Approved', 'Rejected'])
    expect(byName.requested_by?.values).toBeUndefined()
  })

  /**
   * The two kinds F3.4 added **are** offered, and this assertion used to say the opposite.
   *
   * It was right when it was written: nothing produced a `TablePause` or a `PlayerBan`, and a command
   * offering a value no row can have is a filter that always answers nothing. F3.4 built both
   * producers — `POST /game-tables/{id}/request-pause` and `.../registrations/{id}/request-block` —
   * so the condition the old test encoded is what changed, not the rule behind it. Leaving it
   * inverted would now hide the two kinds an admin most wants to find behind a filter that refuses
   * to name them.
   */
  it('offers the two kinds F3.4 gave a producer to', () => {
    const values = approvalRequestSearchFields(t).find((field) => field.name === 'request_type')?.values

    expect(values).toContainEqual({ value: 'TablePause', label: 'Pausa de mesa' })
    expect(values).toContainEqual({ value: 'PlayerBan', label: 'Veto de jugador' })
  })

  /** The value is what travels to the backend; the label is what is typed and read (#240). */
  it('sends the API spelling and shows the translated label', () => {
    const status = approvalRequestSearchFields(t).find((field) => field.name === 'status')

    expect(status?.values).toContainEqual({ value: 'Pending', label: 'Pendiente' })
  })
})

/**
 * The one query this slice sends without anybody typing it: the tray opens filtered by it, and the
 * three request sections ask `/requests/mine` for it.
 *
 * **It is read by two parsers**, `lib/searchQuery.ts` here and `SearchQueryParser.java` there, so
 * these assertions are about the wire and not about a constant. A query that does not survive the
 * round trip through the box, or that travels a label where a value belongs, does not fail loudly —
 * it silently matches nothing, and the button whose absence depends on it comes back.
 */
describe('PENDING_REQUESTS_QUERY', () => {
  it('reads into the box and back out as the very same query', () => {
    const fields = approvalRequestSearchFields(t)
    const restored = searchQueryOf(PENDING_REQUESTS_QUERY, fields)

    expect(buildSearchQuery(restored, fields)).toBe(PENDING_REQUESTS_QUERY)
  })

  /**
   * It travels in the API's spelling, not in the reader's: the chip shows «Pendiente», the string
   * says `Pending`. Built by `serializeSearchQuery` rather than hand-written, so this is the
   * serializer's own output and not a literal that could drift away from it.
   */
  it('is written with the value that travels, never the label', () => {
    expect(PENDING_REQUESTS_QUERY).toBe('/status Pending')
  })

  /** And it parses back into exactly one criterion: the status, with `Pending` as its only value. */
  it('means one criterion and nothing else', () => {
    const fields = approvalRequestSearchFields(t)

    expect(searchQueryOf(PENDING_REQUESTS_QUERY, fields).terms).toEqual([{ field: 'status', values: ['Pending'], connector: 'and' }])
  })
})
