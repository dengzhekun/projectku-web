import { expect, test } from '@playwright/test'

import { cancelOrder, clearCart, fetchFirstProduct, loginByApi } from './helpers/session'

test('authenticated shopper can submit checkout and reach cashier', async ({ page, request }) => {
  const session = await loginByApi(request)
  await clearCart(request, session.token)
  const product = await fetchFirstProduct(request)

  await page.goto(`/products/${product.id}`)
  await page.locator('.btnAdd').click()

  await page.goto('/checkout')
  await expect(page.locator('[aria-label="商品清单"] .item')).toHaveCount(1)
  await expect(page.locator('.payBtn')).toBeEnabled()

  await Promise.all([
    page.waitForURL(/\/cashier\?orderId=/),
    page.locator('.payBtn').click(),
  ])

  const orderId = new URL(page.url()).searchParams.get('orderId')
  expect(orderId).toBeTruthy()

  await cancelOrder(request, session.token, String(orderId))
  await clearCart(request, session.token)
})
