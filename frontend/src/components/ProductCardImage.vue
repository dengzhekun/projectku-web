<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = defineProps<{
  src: string
  alt: string
  variant?: 'card' | 'detail' | 'thumb'
}>()

type Crop = {
  left: number
  top: number
  right: number
  bottom: number
}

const imageEl = ref<HTMLImageElement | null>(null)
const bgColor = ref('#f7f7f5')
const bgMode = ref<'adaptive' | 'fallback'>('fallback')
const crop = ref<Crop | null>(null)
const variant = computed(() => props.variant ?? 'card')

const DEFAULT_BG_COLOR = '#f7f7f5'
const DEFAULT_BG_RGB: [number, number, number] = [247, 247, 245]
const PURE_WHITE = '#ffffff'
const PURE_BLACK = '#080808'
const EDGE_CLUSTER_DISTANCE = 28
const EDGE_SIMILAR_DISTANCE = 36
const EDGE_DOMINANCE_MIN = 0.48
const EDGE_DEVIATION_MAX = 18
const EDGE_SPREAD_MAX = 120
const EDGE_SAMPLE_STEPS = 8
const MAX_ANALYSIS_SIDE = 160
const ALPHA_IGNORE_THRESHOLD = 20
const FOREGROUND_DISTANCE_THRESHOLD = 34

const imageStyle = computed(() => {
  const c = crop.value
  const variantValue = variant.value
  if (!c) {
    return {
      clipPath: 'none',
      transform: 'none',
      width: '100%',
      height: '100%',
    }
  }
  const widthPct = c.right - c.left
  const heightPct = c.bottom - c.top
  const maxScale = variantValue === 'detail' ? 1.28 : variantValue === 'thumb' ? 1.15 : 1.35
  const scale = Math.min(100 / widthPct, 100 / heightPct, maxScale)
  const translateX = -c.left + (100 / scale - widthPct) / 2
  const translateY = -c.top + (100 / scale - heightPct) / 2
  return {
    clipPath: `inset(${c.top}% ${100 - c.right}% ${100 - c.bottom}% ${c.left}%)`,
    transform: `scale(${scale}) translate(${translateX}%, ${translateY}%)`,
    width: `${widthPct}%`,
    height: `${heightPct}%`,
  }
})

const colorDistance = (a: readonly number[], b: readonly number[]) => {
  const dr = a[0] - b[0]
  const dg = a[1] - b[1]
  const db = a[2] - b[2]
  return Math.sqrt(dr * dr + dg * dg + db * db)
}

const toCssRgb = (rgb: readonly number[]) => `rgb(${rgb[0]}, ${rgb[1]}, ${rgb[2]})`

const colorChroma = (rgb: readonly number[]) => Math.max(...rgb) - Math.min(...rgb)

const colorLuma = (rgb: readonly number[]) => 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]

const clampDisplayColor = (rgb: readonly number[]) => {
  const luma = colorLuma(rgb)
  const chroma = colorChroma(rgb)

  if (luma >= 244 && chroma <= 22) return PURE_WHITE
  if (luma <= 22 && chroma <= 22) return PURE_BLACK

  return toCssRgb(rgb)
}

type Rgb = [number, number, number]

const inferEdgeBackground = (samples: Rgb[]) => {
  const clusters: { color: Rgb; count: number }[] = []
  samples.forEach((sample) => {
    const cluster = clusters.find((item) => colorDistance(sample, item.color) <= EDGE_CLUSTER_DISTANCE)
    if (cluster) {
      const nextCount = cluster.count + 1
      cluster.color = [
        Math.round((cluster.color[0] * cluster.count + sample[0]) / nextCount),
        Math.round((cluster.color[1] * cluster.count + sample[1]) / nextCount),
        Math.round((cluster.color[2] * cluster.count + sample[2]) / nextCount),
      ]
      cluster.count += 1
    } else {
      clusters.push({ color: sample, count: 1 })
    }
  })

  const dominant = clusters.sort((a, b) => b.count - a.count)[0]
  if (!dominant) return { color: DEFAULT_BG_RGB, reliable: false }

  const dominance = dominant.count / samples.length
  const similarSamples = samples.filter((sample) => colorDistance(sample, dominant.color) <= EDGE_SIMILAR_DISTANCE)
  const averageDeviation =
    similarSamples.reduce((sum, sample) => sum + colorDistance(sample, dominant.color), 0) /
    Math.max(1, similarSamples.length)
  const edgeSpread = samples.reduce((max, sample) => Math.max(max, colorDistance(sample, dominant.color)), 0)

  return {
    color: dominant.color,
    reliable:
      dominance >= EDGE_DOMINANCE_MIN &&
      averageDeviation <= EDGE_DEVIATION_MAX &&
      edgeSpread <= EDGE_SPREAD_MAX,
  }
}

const analyzeImage = () => {
  const img = imageEl.value
  if (!img || !img.complete || !img.naturalWidth || !img.naturalHeight) return

  try {
    const canvas = document.createElement('canvas')
    const scale = Math.min(1, MAX_ANALYSIS_SIDE / Math.max(img.naturalWidth, img.naturalHeight))
    const width = Math.max(1, Math.round(img.naturalWidth * scale))
    const height = Math.max(1, Math.round(img.naturalHeight * scale))
    canvas.width = width
    canvas.height = height

    const ctx = canvas.getContext('2d', { willReadFrequently: true })
    if (!ctx) return
    ctx.drawImage(img, 0, 0, width, height)

    const edgePoints: [number, number][] = []
    for (let i = 0; i <= EDGE_SAMPLE_STEPS; i += 1) {
      const x = Math.round(1 + ((width - 3) * i) / EDGE_SAMPLE_STEPS)
      const y = Math.round(1 + ((height - 3) * i) / EDGE_SAMPLE_STEPS)
      edgePoints.push([x, 1], [x, height - 2], [1, y], [width - 2, y])
    }
    const edgeSamples: Rgb[] = edgePoints.map(([x, y]) => {
      const px = ctx.getImageData(Math.max(0, x), Math.max(0, y), 1, 1).data
      return [px[0], px[1], px[2]]
    })
    const edgeBackground = inferEdgeBackground(edgeSamples)
    const background = edgeBackground.color
    bgMode.value = edgeBackground.reliable ? 'adaptive' : 'fallback'
    bgColor.value = edgeBackground.reliable ? clampDisplayColor(background) : DEFAULT_BG_COLOR

    const data = ctx.getImageData(0, 0, width, height).data
    let minX = width
    let minY = height
    let maxX = -1
    let maxY = -1

    for (let y = 0; y < height; y += 1) {
      for (let x = 0; x < width; x += 1) {
        const i = (y * width + x) * 4
        if (data[i + 3] < ALPHA_IGNORE_THRESHOLD) continue
        const distance = colorDistance([data[i], data[i + 1], data[i + 2]], background)
        if (distance > FOREGROUND_DISTANCE_THRESHOLD) {
          minX = Math.min(minX, x)
          minY = Math.min(minY, y)
          maxX = Math.max(maxX, x)
          maxY = Math.max(maxY, y)
        }
      }
    }

    if (maxX <= minX || maxY <= minY) {
      crop.value = null
      return
    }

    const padX = Math.max(2, Math.round(width * 0.04))
    const padY = Math.max(2, Math.round(height * 0.04))
    minX = Math.max(0, minX - padX)
    minY = Math.max(0, minY - padY)
    maxX = Math.min(width - 1, maxX + padX)
    maxY = Math.min(height - 1, maxY + padY)

    const cropWidth = maxX - minX + 1
    const cropHeight = maxY - minY + 1
    const areaRatio = (cropWidth * cropHeight) / (width * height)
    const minUsefulCrop = variant.value === 'detail' ? 0.42 : 0.28
    if (areaRatio > 0.88 || areaRatio < minUsefulCrop) {
      crop.value = null
      return
    }

    crop.value = {
      left: (minX / width) * 100,
      top: (minY / height) * 100,
      right: ((maxX + 1) / width) * 100,
      bottom: ((maxY + 1) / height) * 100,
    }
  } catch {
    bgMode.value = 'fallback'
    bgColor.value = DEFAULT_BG_COLOR
    crop.value = null
  }
}

watch(
  () => props.src,
  () => {
    bgColor.value = DEFAULT_BG_COLOR
    bgMode.value = 'fallback'
    crop.value = null
  },
)
</script>

<template>
  <div class="productImage" :class="`productImage--${variant}`" :data-bg-mode="bgMode">
    <img
      ref="imageEl"
      class="productImageMain"
      :src="src"
      :alt="alt"
      :loading="variant === 'detail' ? 'eager' : 'lazy'"
      decoding="async"
      :style="imageStyle"
      @load="analyzeImage"
    />
  </div>
</template>

<style scoped>
.productImage {
  position: relative;
  width: 100%;
  height: 140px;
  overflow: hidden;
  background: v-bind(bgColor);
}

.productImage--detail {
  height: 463px;
  border-radius: 10px;
}

.productImage--thumb {
  width: 76px;
  height: 76px;
  border-radius: 8px;
}

.productImageMain {
  position: absolute;
  inset: 8px 10px;
  display: block;
  width: calc(100% - 20px);
  height: calc(100% - 16px);
  object-fit: contain;
  object-position: center;
  transform-origin: top left;
}

@media (min-width: 900px) {
  .productImage--card {
    height: 170px;
  }
}

@media (max-width: 540px) {
  .productImage--detail {
    height: 320px;
  }
}
</style>
