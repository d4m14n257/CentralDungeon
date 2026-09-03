import { test, expect, type APIRequestContext, type Browser } from '@playwright/test'

/**
 * The language switch (#198), end to end and against the real application.
 *
 * What no unit test proves and this does: that the browser's own language decides what a person
 * sees the first time, that the choice survives a reload, and that `<html lang>` follows — a page
 * that claims Spanish while showing English is read out with the wrong phonemes.
 *
 * Login through TestLoginController (the backend's `test` profile), like every other spec.
 */
const BACKEND_URL = 'http://localhost:8080'
const runId = Math.random().toString(36).slice(2, 10)

async function testLogin(request: APIRequestContext, discordId: string) {
  const response = await request.post(`${BACKEND_URL}/api/v1/auth/test-login`, { params: { discordId } })
  expect(response.ok()).toBeTruthy()
}

/** A context that claims a given browser language, which is what the first visit is decided on. */
async function contextSpeaking(browser: Browser, locale: string, discordId: string) {
  const context = await browser.newContext({ locale })
  await testLogin(context.request, discordId)
  const page = await context.newPage()
  return { context, page }
}

test('an English browser gets English on the first visit, with no choice stored', async ({ browser }) => {
  const { context, page } = await contextSpeaking(browser, 'en-US', `e2e-lang-en-${runId}`)
  try {
    await page.goto('/')
    await expect(page.getByRole('heading', { name: 'Browse tables' })).toBeVisible()
    await expect(page.locator('html')).toHaveAttribute('lang', 'en')
  } finally {
    await context.close()
  }
})

/** The fallback of #198: a language the application does not speak is not a reason to guess. */
test('a browser in a language the app does not speak falls back to Spanish', async ({ browser }) => {
  const { context, page } = await contextSpeaking(browser, 'fr-FR', `e2e-lang-fr-${runId}`)
  try {
    await page.goto('/')
    await expect(page.getByRole('heading', { name: 'Explorar mesas' })).toBeVisible()
    await expect(page.locator('html')).toHaveAttribute('lang', 'es')
  } finally {
    await context.close()
  }
})

test('choosing a language sticks, and outlasts a reload', async ({ browser }) => {
  const { context, page } = await contextSpeaking(browser, 'es-AR', `e2e-lang-switch-${runId}`)
  try {
    await page.goto('/')
    await expect(page.getByRole('heading', { name: 'Explorar mesas' })).toBeVisible()

    await page.getByRole('button', { name: 'Mi cuenta' }).click()
    await page.getByRole('menuitem', { name: 'English' }).click()

    // The labels follow immediately, without a reload.
    await expect(page.getByRole('heading', { name: 'Browse tables' })).toBeVisible()
    await expect(page.locator('html')).toHaveAttribute('lang', 'en')

    // And the choice beats the browser's own language on the way back in.
    await page.reload()
    await expect(page.getByRole('heading', { name: 'Browse tables' })).toBeVisible()
  } finally {
    await context.close()
  }
})
