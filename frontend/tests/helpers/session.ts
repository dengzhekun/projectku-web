import { mkdir } from 'node:fs/promises'
import { dirname } from 'node:path'

import type { APIRequestContext, Page } from '@playwright/test'
import { expect } from '@playwright/test'

export const authFile = 'tests/.auth/user.json'
export const apiBaseURL = process.env.PLAYWRIGHT_API_BASE_URL ?? 'http://127.0.0.1:8080/api'
export const testAccount = process.env.PLAYWRIGHT_ACCOUNT ?? 'user@example.com'
export const testPassword = process.env.PLAYWRIGHT_PASSWORD ?? '123456'

type LoginSession = {
  token: string
  expiresIn: number
  user: { id: string; nickname: string }
}

type ProductSummary = {
  id: string
  name: string
}

const authHeaders = (token: string) => ({
  Authorization: `Bearer ${token}`,
  Accept: 'application/json',
})

export const loginByApi = async (request: APIRequestContext): Promise<LoginSession> => {
  const response = await request.post(`${apiBaseURL}/v1/auth/login`, {
    data: {
      account: testAccount,
      password: testPassword,
    },
  })
  expect(response.ok()).toBeTruthy()
  const payload = await response.json()
  const data = payload?.data
  expect(data?.token).toBeTruthy()
  return {
    token: String(data.token),
    expiresIn: Number(data.expiresIn ?? 7200),
    user: {
      id: String(data.user?.id ?? ''),
      nickname: String(data.user?.nickname ?? '测试用户'),
    },
  }
}

export const writeAuthStorage = async (page: Page, session: LoginSession) => {
  await mkdir(dirname(authFile), { recursive: true })
  await page.goto('/')
  await page.evaluate((snapshot) => {
    window.localStorage.setItem(
      'auth:v1',
      JSON.stringify({
        v: 2,
        user: snapshot.user,
        token: snapshot.token,
        expiresAt: Date.now() + snapshot.expiresIn * 1000,
      }),
    )
  }, session)
  await page.context().storageState({ path: authFile })
}

export const fetchFirstProduct = async (request: APIRequestContext): Promise<ProductSummary> => {
  const response = await request.get(`${apiBaseURL}/v1/products?page=1&size=10`)
  expect(response.ok()).toBeTruthy()
  const payload = await response.json()
  const products = Array.isArray(payload?.data) ? payload.data : []
  expect(products.length).toBeGreaterThan(0)
  const first = products[0]
  return {
    id: String(first.id),
    name: String(first.name ?? '商品'),
  }
}

export const clearCart = async (request: APIRequestContext, token: string) => {
  const listResponse = await request.get(`${apiBaseURL}/v1/cart`, {
    headers: authHeaders(token),
  })
  expect(listResponse.ok()).toBeTruthy()
  const payload = await listResponse.json()
  const items = Array.isArray(payload?.data) ? payload.data : []
  for (const item of items) {
    if (item?.id == null) continue
    const response = await request.delete(`${apiBaseURL}/v1/cart/items/${encodeURIComponent(String(item.id))}`, {
      headers: authHeaders(token),
    })
    expect(response.ok()).toBeTruthy()
  }
}

export const cancelOrder = async (request: APIRequestContext, token: string, orderId: string) => {
  const response = await request.post(`${apiBaseURL}/v1/orders/${encodeURIComponent(orderId)}/cancel`, {
    headers: authHeaders(token),
  })
  expect(response.ok()).toBeTruthy()
}
