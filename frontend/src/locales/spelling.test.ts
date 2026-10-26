import { describe, expect, it } from 'vitest'

import enAdmin from './en/admin.json'
import enMaster from './en/master.json'
import esMaster from './es/master.json'

describe('table status texts', () => {
  /** The English bundle spells it with two l's everywhere; one stray American spelling reads as a typo. */
  it('spell "cancelled" the same way in the badge and in the notice', () => {
    expect(enMaster.notice.Canceled.title).toBe('Cancelled before the end')
    expect(enAdmin.tables.notice.Canceled.title).toBe('Cancelled')
  })

  /** A description that repeats its title tells the reader nothing more. */
  it('does not open the in-progress description with its own title', () => {
    expect(esMaster.notice.InProgress.description.startsWith(esMaster.notice.InProgress.title)).toBe(false)
    expect(enMaster.notice.InProgress.description.toLowerCase()).not.toContain('is being played')
  })
})
