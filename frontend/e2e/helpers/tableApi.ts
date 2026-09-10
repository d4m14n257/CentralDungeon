/**
 * The seeded catalog values, by the ids `V3__catalog_seed.sql` writes.
 *
 * Hard-coded rather than looked up: they are seed constants, and a spec that had to fetch them first
 * would be testing the catalog endpoint on its way to testing something else. If the seed ever
 * changes these ids, the specs fail loudly, which is the right outcome.
 */
export const SEEDED_SYSTEM_ID = '2e5e8e90-104e-4ba7-b6dc-04104a2de237'
export const SEEDED_PLATFORM_ID = 'f36eead8-4ecd-4318-ace6-48d5bcd38b09'
export const SEEDED_TAG_ID = '5092e308-05ea-4555-a983-f04f0ba03c7a'

/** What a table needs to be created, and nothing more. */
export interface RunnableTableBody {
  name: string
  systemIds: string[]
  tagIds: string[]
  platformIds: string[]
  schedule: { weekday: string; hourtime: string; duration: string }[]
  [key: string]: unknown
}

/**
 * The body of a `POST /game-tables` that the backend will accept.
 *
 * A table cannot be created without saying what is played, how it is labelled, where and when
 * (#226, #229), so a spec that only cares about what happens *after* the table exists still has to
 * carry all of it. This is that minimum, in one place, so the next required field is one edit
 * instead of one per spec.
 *
 * @param name  the table's name
 * @param extra anything the spec cares about on top - `maxPlayers`, `duration`, a longer agenda
 * @returns the request body
 */
export function runnableTableBody(name: string, extra: Record<string, unknown> = {}): RunnableTableBody {
  return {
    name,
    systemIds: [SEEDED_SYSTEM_ID],
    tagIds: [SEEDED_TAG_ID],
    platformIds: [SEEDED_PLATFORM_ID],
    schedule: [{ weekday: 'Friday', hourtime: '20:00', duration: '03:00' }],
    ...extra,
  }
}
