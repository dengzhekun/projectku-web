import { expect, test } from '@playwright/test'

import { testAccount, testPassword } from './helpers/session'

test.use({ storageState: { cookies: [], origins: [] } })

test('seeded user can log in through the UI', async ({ page }) => {
  await page.goto('/login')

  await page.locator('#account').fill(testAccount)
  await page.locator('#password').fill(testPassword)

  await Promise.all([
    page.waitForURL(/\/$/),
    page.locator('form button[type="submit"]').click(),
  ])

  const snapshot = await page.evaluate(() => window.localStorage.getItem('auth:v1'))
  expect(snapshot).toContain('"token"')
})
