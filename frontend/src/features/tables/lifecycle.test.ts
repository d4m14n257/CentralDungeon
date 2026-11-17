import { describe, expect, it } from 'vitest'

import { acceptsApplications } from './lifecycle'

describe('acceptsApplications', () => {
  it('takes applications on an opened table, seeking or not', () => {
    expect(acceptsApplications({ status: 'Opened', seekingPlayers: false })).toBe(true)
    expect(acceptsApplications({ status: 'Opened', seekingPlayers: true })).toBe(true)
  })

  it('takes them on a running table only while it is looking for players', () => {
    expect(acceptsApplications({ status: 'InProgress', seekingPlayers: true })).toBe(true)
    expect(acceptsApplications({ status: 'InProgress', seekingPlayers: false })).toBe(false)
  })

  it('takes none in any other status, whatever the flag says', () => {
    for (const status of ['Draft', 'Preparation', 'PauseRequested', 'Pause', 'Finished', 'Canceled'] as const) {
      expect(acceptsApplications({ status, seekingPlayers: true })).toBe(false)
    }
  })
})
