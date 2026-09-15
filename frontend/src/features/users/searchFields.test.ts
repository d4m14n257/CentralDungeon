import { describe, expect, it } from 'vitest'

import i18n from '@/providers/i18n'
import { adminUserSearchFields, userSearchFields } from './searchFields'

const tAdmin = i18n.getFixedT('es', 'admin')
const tUsers = i18n.getFixedT('es', 'users')

describe('adminUserSearchFields', () => {
  it('offers the four commands of F3.1, in order', () => {
    expect(adminUserSearchFields(tAdmin).map((field) => field.name)).toEqual(['discord_name', 'user_name', 'role', 'status'])
  })

  /**
   * #240: a command whose values are a closed set declares them, and that is what turns it into a
   * list the box offers instead of something somebody has to guess the spelling of.
   */
  it('declares fixed choices for /role and /status, and free text for the two names', () => {
    const fields = adminUserSearchFields(tAdmin)
    const byName = Object.fromEntries(fields.map((field) => [field.name, field]))

    expect(byName.role?.values?.map((choice) => choice.value)).toEqual(['Player', 'Master', 'Admin', 'Owner'])
    expect(byName.status?.values?.map((choice) => choice.value)).toEqual(['Allowed', 'Blocked', 'Deleted'])
    expect(byName.discord_name?.values).toBeUndefined()
    expect(byName.user_name?.values).toBeUndefined()
  })

  /** The value is what travels to the backend; the label is what is typed and read (#240). */
  it('sends the API spelling and shows the translated label', () => {
    const role = adminUserSearchFields(tAdmin).find((field) => field.name === 'role')

    expect(role?.values).toContainEqual({ value: 'Owner', label: 'Owner' })
    expect(role?.values).toContainEqual({ value: 'Player', label: 'Jugador' })
  })

  /**
   * The picker's list is a different list on purpose: every account it can reach is `Allowed` by
   * construction, so `/status` there would be a command with one possible answer.
   */
  it('leaves the picker`s own commands untouched', () => {
    expect(userSearchFields(tUsers).map((field) => field.name)).toEqual(['discord_name', 'user_name'])
  })
})
