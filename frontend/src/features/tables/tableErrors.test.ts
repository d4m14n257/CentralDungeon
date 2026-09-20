import { describe, expect, it } from 'vitest'

import { ApiError } from '@/types/api'

import { tableActionErrorMessage } from './tableErrors'

function conflict(errorParams?: Record<string, string>) {
  return new ApiError(409, {
    title: 'Conflict',
    status: 409,
    detail: 'Cannot resume: agenda overlaps table Las minas de Phandelver',
    errorCode: 'SCHEDULE_CONFLICT',
    ...(errorParams === undefined ? {} : { errorParams }),
  })
}

describe('tableActionErrorMessage', () => {
  it('says nothing while nothing has failed', () => {
    expect(tableActionErrorMessage(null)).toBeNull()
    expect(tableActionErrorMessage(undefined)).toBeNull()
  })

  /**
   * #193, the whole reason this exists: the refusal carries the name of the table the agenda now
   * collides with, and that name is the one fact that makes the problem solvable.
   */
  it('carries the other table’s name out of the refusal', () => {
    expect(tableActionErrorMessage(conflict({ otherTableName: 'Las minas de Phandelver' }))).toEqual({
      key: 'tables.errors.SCHEDULE_CONFLICT',
      params: { otherTableName: 'Las minas de Phandelver' },
    })
  })

  /**
   * And without the parameter it falls back, rather than rendering «se pisa con «»».
   *
   * A sentence with a hole where the answer should be says less than an honest generic one — and the
   * specific sentence only earns its place *because* of the name it carries.
   */
  it('falls back when the code arrives without its parameter', () => {
    expect(tableActionErrorMessage(conflict())).toEqual({ key: 'tables.errors.generic', params: {} })
    expect(tableActionErrorMessage(conflict({ otherTableName: '' }))).toEqual({ key: 'tables.errors.generic', params: {} })
  })

  /**
   * Everything else is generic on purpose. Guessing at a message for a code nobody committed to is
   * how an interface ends up asserting something the server never said (#197).
   */
  it('answers generically to any other refusal, and to no answer at all', () => {
    const other = new ApiError(409, { title: 'Conflict', status: 409, detail: 'nope', errorCode: 'INVALID_STATUS_TRANSITION' })

    expect(tableActionErrorMessage(other)).toEqual({ key: 'tables.errors.generic', params: {} })
    expect(tableActionErrorMessage(new Error('the backend never answered'))).toEqual({ key: 'tables.errors.generic', params: {} })
  })
})
