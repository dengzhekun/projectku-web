import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { api } from '../lib/api'
import { resolvePrimaryProductCover } from '../lib/productMedia'

export type FavoriteItem = {
  favId: string
  productId: string
  title: string
  cover: string
  price: number
  oldPrice?: number
  rating: number
  sold: number
  tags: string[] | string
  promo?: string
}

const toText = (value: unknown) => String(value ?? '').trim()

const toNumber = (value: unknown, fallback = 0) => {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : fallback
}

const toStringArray = (value: unknown): string[] => {
  if (Array.isArray(value)) return value.map((x) => toText(x)).filter(Boolean)
  if (typeof value !== 'string') return []
  const raw = value.trim()
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw) as unknown
    if (Array.isArray(parsed)) return parsed.map((x) => toText(x)).filter(Boolean)
  } catch {}
  return raw
    .split(/[|,]/)
    .map((x) => x.trim())
    .filter(Boolean)
}

const normalizeFavorite = (raw: any, productFallback?: any): FavoriteItem => {
  const favId = toText(raw?.favId ?? raw?.id)
  const productId = toText(raw?.productId ?? productFallback?.id)
  const title = toText(raw?.title ?? raw?.productName ?? productFallback?.name ?? productFallback?.title ?? '商品')
  const merged = { ...(productFallback ?? {}), ...(raw ?? {}) }
  const cover =
    resolvePrimaryProductCover(merged, title, undefined, productId) ||
    resolvePrimaryProductCover(merged, title) ||
    ''

  const oldPrice = toNumber(raw?.oldPrice ?? productFallback?.oldPrice, NaN)

  return {
    favId,
    productId,
    title,
    cover,
    price: toNumber(raw?.price ?? productFallback?.price),
    oldPrice: Number.isFinite(oldPrice) && oldPrice > 0 ? oldPrice : undefined,
    rating: toNumber(raw?.rating ?? productFallback?.rating, 0),
    sold: toNumber(raw?.sold ?? productFallback?.sold, 0),
    tags: toStringArray(raw?.tags ?? productFallback?.tags),
    promo: toText(raw?.promo ?? productFallback?.promo) || undefined,
  }
}

export const useFavoritesStore = defineStore('favorites', () => {
  const items = ref<FavoriteItem[]>([])
  const loading = ref(false)

  const count = computed(() => items.value.length)

  const fetch = async () => {
    loading.value = true
    try {
      const res = await api.get('/v1/favorites')
      const data = Array.isArray(res.data?.data) ? res.data.data : []

      const pendingIds: string[] = Array.from(
        new Set(
          data
            .map((row: any) => toText(row?.productId))
            .filter((id: string) => Boolean(id))
            .filter((id: string) => {
              const row = data.find((x: any) => toText(x?.productId) === id)
              if (!row) return false
              const title = toText(row?.title ?? row?.productName ?? '商品')
              return !resolvePrimaryProductCover(row, title)
            }),
        ),
      )

      const productMap = new Map<string, any>()
      await Promise.all(
        pendingIds.map(async (productId: string) => {
          try {
            const detail = await api.get(`/v1/products/${encodeURIComponent(productId)}`)
            productMap.set(productId, detail.data?.data ?? {})
          } catch {}
        }),
      )

      items.value = data.map((row: any) => normalizeFavorite(row, productMap.get(toText(row?.productId))))
    } catch (error) {
      console.error('Failed to fetch favorites:', error)
    } finally {
      loading.value = false
    }
  }

  const add = async (input: { productId: string }) => {
    try {
      await api.post('/v1/favorites', { productId: input.productId })
      await fetch()
    } catch (error) {
      console.error('Failed to add favorite:', error)
      throw error
    }
  }

  const remove = async (favId: string) => {
    try {
      await api.delete(`/v1/favorites/${favId}`)
      items.value = items.value.filter((x) => x.favId.toString() !== favId.toString())
    } catch (error) {
      console.error('Failed to remove favorite:', error)
      throw error
    }
  }

  const removeByProductId = async (productId: string) => {
    try {
      await api.delete(`/v1/favorites/product/${productId}`)
      await fetch()
    } catch (error) {
      console.error('Failed to remove favorite by product id:', error)
      throw error
    }
  }

  const removeMany = async (favIds: string[]) => {
    try {
      await api.delete('/v1/favorites/bulk', { data: { favIds } })
      const set = new Set(favIds.map((id) => id.toString()))
      items.value = items.value.filter((x) => !set.has(x.favId.toString()))
    } catch (error) {
      console.error('Failed to remove many favorites:', error)
      throw error
    }
  }

  const clear = () => {
    items.value = []
  }

  const isFavorite = (productId: string) => {
    return items.value.some((x) => x.productId.toString() === productId.toString())
  }

  return { items, count, loading, fetch, add, remove, removeByProductId, removeMany, clear, isFavorite }
})
