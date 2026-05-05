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
const bgColor = ref('#f8f8f6')
const crop = ref<Crop | null>(null)

const imageStyle = computed(() => {
  const c = crop.value
  const variant = props.variant ?? 'card'
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
  const maxScale = variant === 'detail' ? 1.28 : variant === 'thumb' ? 1.15 : 1.35
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

const colorDistance = (a: number[], b: number[]) => {
  const dr = a[0] - b[0]
  const dg = a[1] - b[1]
  const db = a[2] - b[2]
  return Math.sqrt(dr * dr + dg * dg + db * db)
}

const toCssRgb = (rgb: number[]) => `rgb(${rgb[0]}, ${rgb[1]}, ${rgb[2]})`

const averageColor = (samples: number[][]) => {
  const total = samples.reduce(
    (acc, x) => {
      acc[0] += x[0]
      acc[1] += x[1]
      acc[2] += x[2]
      return acc
    },
    [0, 0, 0],
  )
  return total.map((x) => Math.round(x / samples.length))
}

const analyzeImage = () => {
  const img = imageEl.value
  if (!img || !img.complete || !img.naturalWidth || !img.naturalHeight) return

  try {
    const canvas = document.createElement('canvas')
    const maxSide = 160
    const scale = Math.min(1, maxSide / Math.max(img.naturalWidth, img.naturalHeight))
    const width = Math.max(1, Math.round(img.naturalWidth * scale))
    const height = Math.max(1, Math.round(img.naturalHeight * scale))
    canvas.width = width
    canvas.height = height

    const ctx = canvas.getContext('2d', { willReadFrequently: true })
    if (!ctx) return
    ctx.drawImage(img, 0, 0, width, height)

    const points = [
      [1, 1],
      [width - 2, 1],
      [1, height - 2],
      [width - 2, height - 2],
      [Math.floor(width / 2), 1],
      [Math.floor(width / 2), height - 2],
    ].map(([x, y]) => {
      const px = ctx.getImageData(Math.max(0, x), Math.max(0, y), 1, 1).data
      return [px[0], px[1], px[2]]
    })
    const background = averageColor(points)
    bgColor.value = toCssRgb(background)

    const data = ctx.getImageData(0, 0, width, height).data
    let minX = width
    let minY = height
    let maxX = -1
    let maxY = -1
    const threshold = 34

    for (let y = 0; y < height; y += 1) {
      for (let x = 0; x < width; x += 1) {
        const i = (y * width + x) * 4
        if (data[i + 3] < 20) continue
        const distance = colorDistance([data[i], data[i + 1], data[i + 2]], background)
        if (distance > threshold) {
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
    const variant = props.variant ?? 'card'
    const minUsefulCrop = variant === 'detail' ? 0.42 : 0.28
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
    crop.value = null
  }
}

watch(
  () => props.src,
  () => {
    bgColor.value = '#f8f8f6'
    crop.value = null
  },
)
</script>

<template>
  <div class="productImage" :class="`productImage--${variant ?? 'card'}`">
    <img
      ref="imageEl"
      class="productImageMain"
      :src="src"
      :alt="alt"
      loading="lazy"
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
