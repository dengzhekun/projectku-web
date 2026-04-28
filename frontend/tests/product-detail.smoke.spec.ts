import { expect, test } from '@playwright/test'

import { fetchFirstProduct } from './helpers/session'

test('product detail page renders the primary purchase actions', async ({ page, request }) => {
  const product = await fetchFirstProduct(request)

  await page.goto(`/products/${product.id}`)

  await expect(page.locator('.h1')).toContainText(product.name)
  await expect(page.locator('.priceMain')).not.toHaveText('--')
  await expect(page.locator('.btnAdd')).toBeVisible()
  await expect(page.locator('.btnBuy')).toBeVisible()
})
