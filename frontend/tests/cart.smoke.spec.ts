import { expect, test } from '@playwright/test'

import { clearCart, fetchFirstProduct, loginByApi } from './helpers/session'

test('adding a product from detail page shows up in cart', async ({ page, request }) => {
  const session = await loginByApi(request)
  await clearCart(request, session.token)
  const product = await fetchFirstProduct(request)

  await page.goto(`/products/${product.id}`)
  await page.locator('.btnAdd').click()

  await page.goto('/cart')

  const cartItems = page.locator('[aria-label="购物车商品"] > article.item')
  await expect(cartItems).toHaveCount(1)
  await expect(cartItems.first().locator('.name')).toContainText(product.name)
  await expect(page.locator('.footer button')).toBeVisible()

  await clearCart(request, session.token)
})
