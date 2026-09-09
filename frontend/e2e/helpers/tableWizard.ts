import type { Page } from '@playwright/test'

/**
 * The steps of the create-table wizard that every spec has to walk through, in one place.
 *
 * Six specs had their own copy of "fill the name and press Siguiente three times", which worked for
 * as long as the wizard asked for nothing else. #226 made three things required — a system, a
 * platform and at least one weekly slot — and all six copies broke at once. They live here now so
 * the next requirement is one edit.
 */

/** The seeded values (V3__catalog_seed.sql), so a spec never depends on data it did not create. */
const A_SYSTEM = 'D&D 5e'
const A_PLATFORM = 'Discord'

/**
 * Picks the system and the platform the table cannot be created without (#226).
 *
 * Call it while the wizard is on step 2. Tags stay untouched on purpose: they are optional, and a
 * spec that picked one would be asserting a rule that does not exist.
 *
 * @param page the master's page, on the catalogs step
 */
export async function chooseRequiredCatalogs(page: Page): Promise<void> {
  // Filtered by their placeholder rather than found by label: the pickers put their caption in a
  // paragraph, not a <label>, so `getByLabel` finds nothing. Worth fixing in the component one day.
  await page.getByRole('combobox').filter({ hasText: 'Elegí un sistema' }).click()
  await page.getByRole('option', { name: A_SYSTEM, exact: true }).click()
  await page.getByRole('combobox').filter({ hasText: 'Elegí una plataforma' }).click()
  await page.getByRole('option', { name: A_PLATFORM, exact: true }).click()
}

/**
 * Adds one weekly slot to the agenda.
 *
 * Call it while the wizard is on step 3.
 *
 * **A spec that creates two tables for the same master has to give them different days.** R1 of #178
 * refuses a master two live tables with overlapping agendas, and now that every table must carry an
 * agenda (#226), two tables built with the same default would collide - which is the rule working,
 * not a broken spec.
 *
 * @param page     the master's page, on the schedule step
 * @param hourtime the local start time, `HH:mm`
 * @param weekday  the day, as the selector spells it. Defaults to the editor's own default
 */
export async function addScheduleSlot(page: Page, hourtime: string, weekday?: string): Promise<void> {
  if (weekday) {
    await page.locator('#schedule-weekday').click()
    await page.getByRole('option', { name: weekday, exact: true }).click()
  }
  await page.getByLabel('Hora', { exact: true }).fill(hourtime)
  await page.getByRole('button', { name: 'Agregar' }).click()
}
