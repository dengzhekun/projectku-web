import { getProductCover, type ProductCategoryHint } from './productCovers'

export type ProductMediaInput = Record<string, unknown>

const MEDIA_KEYS = ['media', 'medias', 'images', 'imageList', 'gallery', 'pictures', 'detailImages', 'album']
const COVER_KEYS = ['cover', 'image', 'imageUrl', 'imageURL', 'productImage', 'thumbnail', 'thumb', 'poster']

const toText = (value: unknown) => String(value ?? '').trim()

const pushText = (target: string[], value: unknown) => {
  const text = toText(value)
  if (text) target.push(text)
}

const readStringArray = (value: unknown): string[] => {
  if (Array.isArray(value)) {
    return value.map(toText).filter(Boolean)
  }

  if (typeof value === 'string') {
    const text = value.trim()
    if (!text) return []

    try {
      const parsed = JSON.parse(text) as unknown
      if (Array.isArray(parsed)) return parsed.map(toText).filter(Boolean)
    } catch {}

    if (text.includes(',') || text.includes('|')) {
      return text
        .split(/[|,]/)
        .map((item) => item.trim())
        .filter(Boolean)
    }

    return [text]
  }

  return []
}

const dedupe = (items: string[]) => Array.from(new Set(items.filter(Boolean)))

export const resolveProductMedia = (
  raw: ProductMediaInput,
  fallbackTitle: string,
  hint?: ProductCategoryHint,
  fallbackId?: string,
): string[] => {
  const items: string[] = []

  for (const key of MEDIA_KEYS) {
    items.push(...readStringArray(raw[key]))
  }

  for (const key of COVER_KEYS) {
    pushText(items, raw[key])
  }

  const normalized = dedupe(items)
  if (normalized.length > 0) return normalized

  const fallback = getProductCover(fallbackTitle, hint, String(fallbackId ?? raw.id ?? '').trim())
  return fallback ? [fallback] : []
}

export const resolvePrimaryProductCover = (
  raw: ProductMediaInput,
  fallbackTitle: string,
  hint?: ProductCategoryHint,
  fallbackId?: string,
): string => {
  const [first] = resolveProductMedia(raw, fallbackTitle, hint, fallbackId)
  return first || ''
}
