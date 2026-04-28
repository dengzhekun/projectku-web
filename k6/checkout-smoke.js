import http from 'k6/http'
import { check, fail, sleep } from 'k6'

export const options = {
  vus: Number(__ENV.VUS || 1),
  iterations: Number(__ENV.ITERATIONS || 1),
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<2000'],
  },
}

const baseUrl = (__ENV.BASE_URL || 'http://127.0.0.1:8080/api').replace(/\/$/, '')
const account = __ENV.ACCOUNT || 'user@example.com'
const password = __ENV.PASSWORD || '123456'

function parseData(response, step) {
  let payload = null
  try {
    payload = response.json()
  } catch (error) {
    fail(`${step}: response is not valid JSON`)
  }
  if (!payload) {
    fail(`${step}: empty response payload`)
  }
  return payload.data
}

function authHeaders(token) {
  return {
    Authorization: `Bearer ${token}`,
    Accept: 'application/json',
    'Content-Type': 'application/json',
  }
}

function login() {
  const response = http.post(
    `${baseUrl}/v1/auth/login`,
    JSON.stringify({ account, password }),
    { headers: { 'Content-Type': 'application/json', Accept: 'application/json' } },
  )
  check(response, { 'login 200': (r) => r.status === 200 }) || fail('login failed')
  const data = parseData(response, 'login')
  if (!data?.token) {
    fail('login: missing token')
  }
  return String(data.token)
}

function fetchFirstProduct() {
  const response = http.get(`${baseUrl}/v1/products?page=1&size=10`, { headers: { Accept: 'application/json' } })
  check(response, { 'products 200': (r) => r.status === 200 }) || fail('products request failed')
  const data = parseData(response, 'products')
  if (!Array.isArray(data) || data.length === 0) {
    fail('products: no test product available')
  }
  return data[0]
}

function clearCart(token) {
  const response = http.get(`${baseUrl}/v1/cart`, { headers: authHeaders(token) })
  check(response, { 'cart list 200': (r) => r.status === 200 }) || fail('cart list failed')
  const items = parseData(response, 'cart list')
  if (!Array.isArray(items)) return
  for (const item of items) {
    if (!item?.id) continue
    const removeResponse = http.del(`${baseUrl}/v1/cart/items/${item.id}`, null, { headers: authHeaders(token) })
    check(removeResponse, { 'cart delete 200': (r) => r.status === 200 }) || fail('cart delete failed')
  }
}

function addToCart(token, productId) {
  const response = http.post(
    `${baseUrl}/v1/cart/items`,
    JSON.stringify({ productId, quantity: 1 }),
    { headers: authHeaders(token) },
  )
  check(response, { 'cart add 200': (r) => r.status === 200 }) || fail('cart add failed')
}

function checkout(token) {
  const response = http.post(
    `${baseUrl}/v1/orders/checkout`,
    JSON.stringify({ addressId: 0, couponCode: '' }),
    { headers: authHeaders(token) },
  )
  check(response, { 'checkout 200': (r) => r.status === 200 }) || fail('checkout failed')
  const order = parseData(response, 'checkout')
  if (!order?.id) {
    fail('checkout: missing order id')
  }
  return String(order.id)
}

function cancelOrder(token, orderId) {
  const response = http.post(`${baseUrl}/v1/orders/${orderId}/cancel`, null, { headers: authHeaders(token) })
  check(response, { 'cancel order 200': (r) => r.status === 200 }) || fail('cancel order failed')
}

export default function () {
  const token = login()
  const product = fetchFirstProduct()
  clearCart(token)
  addToCart(token, product.id)
  const orderId = checkout(token)
  cancelOrder(token, orderId)
  sleep(1)
}
