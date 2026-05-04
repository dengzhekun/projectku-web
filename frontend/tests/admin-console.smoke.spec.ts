import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'

import { apiBaseURL } from './helpers/session'

test.use({ storageState: { cookies: [], origins: [] } })

const loginAdmin = async (page: Page) => {
  const response = await page.request.post(`${apiBaseURL}/v1/auth/login`, {
    data: {
      account: 'admin',
      password: '123456',
    },
  })
  expect(response.ok()).toBeTruthy()
  const payload = await response.json()
  const token = String(payload?.data?.token ?? '')
  expect(token).toBeTruthy()

  await page.goto('/')
  await page.evaluate((snapshot) => {
    window.localStorage.setItem(
      'admin-auth:v1',
      JSON.stringify({
        v: 2,
        account: 'admin',
        token: snapshot.token,
        expiresAt: Date.now() + 7200 * 1000,
        loggedInAt: Date.now(),
      }),
    )
  }, { token })
}

test('admin console routes are reachable after admin login', async ({ page }) => {
  await loginAdmin(page)

  await page.goto('/admin/kb')
  await expect(page.getByText('知识库管理').first()).toBeVisible()
  await expect(page.getByText('AI客服测试台')).toBeVisible()
  await expect(page.getByPlaceholder('输入要验证的客服问题')).toBeVisible()
  await expect(page.getByRole('button', { name: '转为知识库草稿' })).toBeDisabled()

  await page.goto('/admin/payments')
  await expect(page.getByText('支付订单看板').first()).toBeVisible()
})

test('kb tester does not allow realtime product replies to become drafts', async ({ page }) => {
  await loginAdmin(page)

  await page.route('**/api/v1/customer-service/chat', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        message: 'success',
        data: {
          answer: 'iPhone 15 当前有多个规格，实际价格和库存以下单页为准。',
          confidence: 0.88,
          route: 'product',
          sourceType: 'product',
          citations: [{ sourceType: 'product', sourceId: '15', title: 'iPhone 15' }],
          hitLogs: [],
          fallbackReason: null,
          retrievalTrace: {
            route: 'product',
            sourceType: 'product',
            returnedChunkCount: 0,
            selectedChunkCount: 0,
          },
        },
      }),
    })
  })

  await page.goto('/admin/kb')
  await page.getByPlaceholder('输入要验证的客服问题').fill('苹果15多少钱')
  await page.getByRole('button', { name: '测试回答' }).click()

  await expect(page.getByText('商品实时查询')).toBeVisible()
  await expect(page.getByText('该结果来自实时业务接口，不适合沉淀为知识库草稿。')).toBeVisible()
  await expect(page.getByRole('button', { name: '转为知识库草稿' })).toBeDisabled()
})
