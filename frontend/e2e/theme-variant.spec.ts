import { test, expect } from '@playwright/test'

/**
 * The `dark:` classes of the generated shadcn components follow the app's `data-theme`, not the OS
 * preference. It is a browser test because only a real CSS engine resolves a variant: jsdom cannot.
 * The probe uses `dark:bg-input/30`, the class of the outline button, the input and the select.
 */
async function probeBackground(page: import('@playwright/test').Page, theme: 'dark' | 'light') {
  await page.evaluate((value) => {
    document.documentElement.setAttribute('data-theme', value)
    document.getElementById('theme-probe')?.remove()
    const probe = document.createElement('div')
    probe.id = 'theme-probe'
    probe.className = 'dark:bg-input/30'
    document.body.appendChild(probe)
  }, theme)
  return page.locator('#theme-probe').evaluate((el) => getComputedStyle(el).backgroundColor)
}

for (const os of ['dark', 'light'] as const) {
  test(`dark: variants follow data-theme while the OS prefers ${os}`, async ({ page }) => {
    await page.emulateMedia({ colorScheme: os })
    await page.goto('/')
    const transparent = 'rgba(0, 0, 0, 0)'
    expect(await probeBackground(page, 'dark')).not.toBe(transparent)
    expect(await probeBackground(page, 'light')).toBe(transparent)
  })
}
