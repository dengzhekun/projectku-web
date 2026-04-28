<script setup lang="ts">
import { ref } from 'vue'

import UiButton from '../ui/UiButton.vue'
import UiInput from '../ui/UiInput.vue'

const props = withDefaults(
  defineProps<{
    loading?: boolean
  }>(),
  {
    loading: false,
  },
)

const emit = defineEmits<{
  (e: 'create-manual', payload: { title: string; category: string; contentText: string }): void
  (e: 'upload', payload: { title: string; category: string; file: File }): void
}>()

const manualTitle = ref('')
const manualCategory = ref('')
const manualContent = ref('')

const uploadTitle = ref('')
const uploadCategory = ref('')
const uploadFile = ref<File | null>(null)

const resetManual = () => {
  manualTitle.value = ''
  manualCategory.value = ''
  manualContent.value = ''
}

const resetUpload = () => {
  uploadTitle.value = ''
  uploadCategory.value = ''
  uploadFile.value = null
}

const submitManual = () => {
  emit('create-manual', {
    title: manualTitle.value,
    category: manualCategory.value,
    contentText: manualContent.value,
  })
  resetManual()
}

const onFileChange = (event: Event) => {
  const target = event.target as HTMLInputElement
  uploadFile.value = target.files?.[0] ?? null
}

const submitUpload = () => {
  if (!uploadFile.value) return
  emit('upload', {
    title: uploadTitle.value,
    category: uploadCategory.value,
    file: uploadFile.value,
  })
  resetUpload()
}
</script>

<template>
  <section class="wrap">
    <article class="panel">
      <div class="head">
        <h3>手工新建</h3>
        <p>直接录入 FAQ、售后规则、物流说明等文本。</p>
      </div>
      <div class="form">
        <UiInput v-model="manualTitle" placeholder="标题" />
        <UiInput v-model="manualCategory" placeholder="分类，例如 policy / faq" />
        <textarea v-model="manualContent" class="textarea" rows="8" placeholder="输入文档内容" />
        <UiButton variant="primary" :loading="props.loading" :disabled="!manualTitle || !manualCategory || !manualContent" @click="submitManual">
          创建文档
        </UiButton>
      </div>
    </article>

    <article class="panel">
      <div class="head">
        <h3>上传文档</h3>
        <p>支持 `txt`、`md`、`docx`，大小建议不超过 2MB。</p>
      </div>
      <div class="form">
        <UiInput v-model="uploadTitle" placeholder="标题" />
        <UiInput v-model="uploadCategory" placeholder="分类，例如 support / logistics" />
        <input class="file" type="file" accept=".txt,.md,.docx" @change="onFileChange" />
        <div class="file-name">{{ uploadFile?.name || '未选择文件' }}</div>
        <UiButton variant="primary" :loading="props.loading" :disabled="!uploadTitle || !uploadCategory || !uploadFile" @click="submitUpload">
          上传并解析
        </UiButton>
      </div>
    </article>
  </section>
</template>

<style scoped>
.wrap {
  display: grid;
  gap: 16px;
}

.panel {
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--bg);
  box-shadow: var(--shadow);
  padding: 18px;
}

.head {
  display: grid;
  gap: 4px;
  margin-bottom: 14px;
}

.head h3 {
  margin: 0;
  font-size: 18px;
  color: var(--text-h);
}

.head p {
  color: var(--text);
  font-size: var(--font-sm);
}

.form {
  display: grid;
  gap: 12px;
}

.textarea,
.file {
  width: 100%;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 12px;
  font: inherit;
  color: var(--text-h);
  background: var(--bg);
}

.textarea {
  resize: vertical;
  min-height: 180px;
}

.file-name {
  color: var(--text);
  font-size: var(--font-sm);
}

@media (min-width: 960px) {
  .wrap {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
