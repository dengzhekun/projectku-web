import type { ProductCategoryHint } from './productCovers'
import { resolvePrimaryProductCover } from './productMedia'

export type BackendProduct = Record<string, unknown>

export type ProductListItem = {
  id: string
  title: string
  price: number
  cover: string
  tags: string[]
  rating: number
  sales: number
  brand: string
  categoryId: string
  sub?: string
}

type MapOptions = {
  categoryId?: string
  hint?: ProductCategoryHint
  fallbackRating?: number
}

const toText = (value: unknown, fallback = '') => {
  const text = String(value ?? '').trim()
  return text || fallback
}

const toNumber = (value: unknown, fallback = 0) => {
  const n = Number(value)
  return Number.isFinite(n) ? n : fallback
}

export const parseProductTags = (value: unknown, extra?: unknown): string[] => {
  const tags = new Set<string>()
  const push = (item: unknown) => {
    const text = toText(item)
    if (text) tags.add(text)
  }

  if (Array.isArray(value)) {
    value.forEach(push)
  } else if (typeof value === 'string') {
    try {
      const parsed = JSON.parse(value) as unknown
      if (Array.isArray(parsed)) parsed.forEach(push)
      else if (parsed && typeof parsed === 'object') Object.values(parsed).forEach(push)
      else push(parsed)
    } catch {
      value.split(/[;,|]/).forEach(push)
    }
  } else if (value && typeof value === 'object') {
    Object.values(value).forEach(push)
  }

  push(extra)
  return Array.from(tags)
}

const inferBrand = (name: string, rawBrand: unknown) => {
  const brand = toText(rawBrand)
  if (brand) return brand

  const normalized = name.toLowerCase()
  const knownBrands = ['Apple', 'iPhone', 'Xiaomi', 'Redmi', 'Huawei', 'OPPO', 'vivo', 'Lenovo', 'ThinkPad', 'Dell', 'HP', 'ASUS', 'ROG', 'Sony', 'Midea', 'Haier']
  return knownBrands.find((item) => normalized.includes(item.toLowerCase())) ?? ''
}

export const mapBackendProduct = (raw: BackendProduct, options: MapOptions = {}): ProductListItem => {
  const id = toText(raw.id)
  const title = toText(raw.name ?? raw.title, '商品')
  const sub = toText(raw.subCategory ?? raw.sub)
  const cover = resolvePrimaryProductCover(raw, title, options.hint, id) || `/product_${id}.jpg`

  return {
    id,
    title,
    price: toNumber(raw.price),
    cover,
    tags: parseProductTags(raw.tags, sub),
    rating: toNumber(raw.rating, options.fallbackRating ?? 4.6),
    sales: toNumber(raw.sold ?? raw.sales),
    brand: inferBrand(title, raw.brand),
    categoryId: toText(options.categoryId ?? raw.categoryId),
    sub: sub || undefined,
  }
}
