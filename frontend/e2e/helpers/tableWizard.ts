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
 * Picks the three catalogs the table cannot be created without (#226, #229).
 *
 * Call it while the wizard is on step 2. Tags joined the list with #229, which reversed the part of
 * #226 that left them optional.
 *
 * @param page the master's page, on the catalogs step
 */
export async function chooseRequiredCatalogs(page: Page): Promise<void> {
  // Filtered by their placeholder rather than found by label: the pickers put their caption in a
  // paragraph, not a <label>, so `getByLabel` finds nothing. Worth fixing in the component one day.
  await page.getByRole('combobox').filter({ hasText: 'Elegí un sistema' }).click()
  await page.getByRole('option', { name: A_SYSTEM, exact: true }).click()
  await page.getByRole('combobox').filter({ hasText: 'Elegí un tag' }).click()
  await page.getByRole('option').first().click()
  await page.getByRole('combobox').filter({ hasText: 'Elegí una plataforma' }).click()
  await page.getByRole('option', { name: A_PLATFORM, exact: true }).click()
}

/**
 * Claims one weekly slot by clicking it on the grid.
 *
 * Since #228 that is the only way in: the day-and-hour form is gone, because having it *and* the
 * grid meant two ways to do one thing. The slot is born three hours long, which is the default the
 * editor gives it.
 *
 * **A spec that creates two tables for the same master has to give them different days.** R1 of #178
 * refuses a master two live tables with overlapping agendas, and every table carries an agenda now
 * (#226), so two built on the same day would collide - which is the rule working, not a broken spec.
 *
 * @param page     the master's page, on the schedule step
 * @param hourtime the local start time, `HH:mm`, as the grid labels it
 * @param weekday  the day, as the grid spells it
 */
export async function addScheduleSlot(page: Page, hourtime: string, weekday = 'Viernes'): Promise<void> {
  await page.getByRole('button', { name: `Ocupar ${weekday} ${hourtime}` }).click()
}
