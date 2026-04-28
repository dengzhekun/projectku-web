import { test as setup } from '@playwright/test'

import { clearCart, loginByApi, writeAuthStorage } from './helpers/session'

setup('create authenticated storage state', async ({ page, request }) => {
  const session = await loginByApi(request)
  await clearCart(request, session.token)
  await writeAuthStorage(page, session)
})
