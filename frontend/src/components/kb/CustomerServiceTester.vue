<script setup lang="ts">
import { computed, ref } from 'vue'

import { askCustomerService, type CustomerServiceReply } from '../../lib/customerService'
import type { KbDocument } from '../../lib/knowledgeBase'
import UiButton from '../ui/UiButton.vue'
import UiInput from '../ui/UiInput.vue'

const props = defineProps<{
  selectedDocument?: KbDocument | null
  creatingDraft?: boolean
}>()

const emit = defineEmits<{
  (
    e: 'create-draft',
    payload: {
      title: string
      category: string
      contentText: string
    },
  ): void
  (e: 'test-finished', payload: { question: string; reply: CustomerServiceReply }): void
}>()

const question = ref('')
const testing = ref(false)
const error = ref('')
const reply = ref<CustomerServiceReply | null>(null)
const lastQuestion = ref('')

const routeLabels: Record<string, string> = {
  product: '商品实时查询',
  wallet: '余额查询',
  order: '订单查询',
  coupon: '优惠券查询',
  aftersales: '售后业务',
  after_sales: '售后规则',
  logistics: '物流规则',
  payment_refund: '支付退款',
  shopping_guide: '导购规则',
  knowledge: '知识库',
}

const sourceLabels: Record<string, string> = {
  business: '实时业务接口',
  product: '商品接口',
  knowledge: 'LightRAG知识库',
}

const trace = computed(() => reply.value?.retrievalTrace ?? null)

const routeValue = computed(() => trace.value?.route || (reply.value as any)?.route || '')
const sourceValue = computed(() => trace.value?.sourceType || (reply.value as any)?.sourceType || '')

const routeLabel = computed(() => {
  const value = routeValue.value
  return value ? routeLabels[value] || value : '-'
})

const sourceLabel = computed(() => {
  const value = sourceValue.value
  return value ? sourceLabels[value] || value : '-'
})

const confidenceLabel = computed(() => {
  const value = reply.value?.confidence
  if (value === null || value === undefined) return '-'
  return Number(value).toFixed(2)
})

const returnedChunkCount = computed(() => trace.value?.returnedChunkCount ?? 0)
const selectedChunkCount = computed(() => trace.value?.selectedChunkCount ?? 0)
const citationCount = computed(() => reply.value?.citations?.length ?? trace.value?.citationCount ?? 0)
const hitLogCount = computed(() => reply.value?.hitLogs?.length ?? trace.value?.hitLogCount ?? 0)

const missDetected = computed(() => {
  if (!reply.value) return false
  if (reply.value.fallbackReason) return true
  const confidence = reply.value.confidence
  if (confidence !== null && confidence !== undefined && Number(confidence) < 0.6) return true
  return selectedChunkCount.value === 0 && sourceValue.value === 'knowledge'
})

const realtimeResult = computed(() => {
  const source = sourceValue.value
  const route = routeValue.value
  return source === 'product' || source === 'business' || ['product', 'wallet', 'order'].includes(route)
})

const knowledgeDraftAllowed = computed(() => {
  if (!reply.value || realtimeResult.value) return false
  return sourceValue.value === 'knowledge'
})

const draftHint = computed(() => {
  if (!reply.value) return ''
  if (realtimeResult.value) return '该结果来自实时业务接口，不适合沉淀为知识库草稿。'
  if (!knowledgeDraftAllowed.value) return '只有知识库规则、政策、FAQ 类回答适合转为草稿。'
  if (missDetected.value) return '建议转为草稿后人工确认，再切分并索引。'
  return '当前回答可用；如仍不够准确，可以转草稿补充标准规则。'
})

const draftDisabled = computed(() => !knowledgeDraftAllowed.value || props.creatingDraft || testing.value)

const selectedScopeText = computed(() => {
  if (!props.selectedDocument) return '当前测试范围：全部已同步到 LightRAG 的知识库'
  return `当前选中文档：${props.selectedDocument.title}。本测试会查询完整 LightRAG 运行态，不强制限定单篇文档。`
})

const runTest = async () => {
  const text = question.value.trim()
  if (!text) {
    error.value = '请输入要验证的问题'
    return
  }
  testing.value = true
  error.value = ''
  reply.value = null
  try {
    const conversationId = `admin-kb-test-${Date.now()}`
    reply.value = await askCustomerService(text, conversationId)
    lastQuestion.value = text
    emit('test-finished', { question: text, reply: reply.value })
  } catch (err: any) {
    error.value = err?.message || 'AI客服测试失败'
  } finally {
    testing.value = false
  }
}

const createDraft = () => {
  if (!reply.value || !lastQuestion.value) return
  if (!knowledgeDraftAllowed.value) return
  const category = props.selectedDocument?.category || '客服FAQ'
  const title = `客服未命中草稿：${lastQuestion.value.slice(0, 24)}`
  const contentText = [
    `问题：${lastQuestion.value}`,
    '',
    '建议答案：',
    reply.value.answer || '请在这里补充标准答案。',
    '',
    '后台测试信息：',
    `路由：${routeLabel.value}`,
    `来源：${sourceLabel.value}`,
    `置信度：${confidenceLabel.value}`,
    reply.value.fallbackReason ? `回退原因：${reply.value.fallbackReason}` : '',
    missDetected.value ? '处理建议：请人工确认答案后，重新切分并索引。' : '处理建议：如答案仍不够准确，可人工补充标准规则后重新索引。',
  ]
    .filter((line) => line !== '')
    .join('\n')

  emit('create-draft', { title, category, contentText })
}
</script>

<template>
  <section class="panel tester">
    <div class="head">
      <div>
        <h3>AI客服测试台</h3>
        <p>{{ selectedScopeText }}</p>
      </div>
      <span class="scope-badge">LightRAG运行态</span>
    </div>

    <div class="question-row">
      <UiInput
        v-model="question"
        placeholder="输入要验证的客服问题"
        :disabled="testing"
        @keydown.enter.prevent="runTest"
      />
      <UiButton variant="primary" :loading="testing" @click="runTest">测试回答</UiButton>
      <UiButton :loading="creatingDraft" :disabled="draftDisabled" @click="createDraft">转为知识库草稿</UiButton>
    </div>

    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="reply" class="result">
      <div class="metric-grid">
        <div class="metric">
          <span>路由</span>
          <strong>{{ routeLabel }}</strong>
        </div>
        <div class="metric">
          <span>来源</span>
          <strong>{{ sourceLabel }}</strong>
        </div>
        <div class="metric">
          <span>置信度</span>
          <strong>{{ confidenceLabel }}</strong>
        </div>
        <div class="metric" :class="{ warn: missDetected }">
          <span>判定</span>
          <strong>{{ missDetected ? '需补充' : '可用' }}</strong>
        </div>
      </div>

      <div class="answer">
        <h4>回答</h4>
        <p>{{ reply.answer }}</p>
      </div>

      <div class="trace-grid">
        <span>返回片段 {{ returnedChunkCount }}</span>
        <span>选中片段 {{ selectedChunkCount }}</span>
        <span>引用 {{ citationCount }}</span>
        <span>命中日志 {{ hitLogCount }}</span>
      </div>

      <div v-if="reply.fallbackReason" class="reason">回退原因：{{ reply.fallbackReason }}</div>
      <div v-if="draftHint" class="draft-hint" :class="{ blocked: realtimeResult }">{{ draftHint }}</div>

      <ul v-if="reply.citations?.length" class="citations">
        <li v-for="citation in reply.citations" :key="`${citation.sourceType}-${citation.sourceId}-${citation.title}`">
          <strong>{{ citation.title }}</strong>
          <span>{{ citation.sourceType }} / {{ citation.sourceId }}</span>
        </li>
      </ul>
    </div>
  </section>
</template>

<style scoped>
.panel {
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--bg);
  box-shadow: var(--shadow);
  padding: 18px;
}

.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.head h3,
.answer h4 {
  margin: 0;
  color: var(--text-h);
}

.head h3 {
  font-size: 18px;
}

.head p,
.reason,
.error,
.trace-grid,
.citations span {
  color: var(--text);
  font-size: var(--font-sm);
}

.scope-badge {
  white-space: nowrap;
  padding: 4px 10px;
  border-radius: var(--radius-pill);
  background: var(--accent-bg);
  color: var(--accent);
  font-size: var(--font-sm);
  font-weight: 800;
}

.question-row {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) auto auto;
  gap: 10px;
}

.error {
  margin: 10px 0 0;
  color: var(--danger, #b42318);
}

.result {
  display: grid;
  gap: 12px;
  margin-top: 14px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.metric {
  display: grid;
  gap: 5px;
  min-height: 64px;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--bg) 94%, var(--accent-bg));
}

.metric span {
  color: var(--text);
  font-size: var(--font-sm);
}

.metric strong {
  color: var(--text-h);
}

.metric.warn {
  border-color: color-mix(in srgb, var(--warning, #c97a00) 28%, var(--border));
  background: color-mix(in srgb, var(--warning, #c97a00) 10%, var(--bg));
}

.answer {
  display: grid;
  gap: 8px;
  padding: 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
}

.answer p {
  margin: 0;
  color: var(--text-h);
  line-height: 1.75;
  white-space: pre-wrap;
}

.trace-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 14px;
}

.reason {
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--warning, #c97a00) 10%, var(--bg));
}

.draft-hint {
  padding: 10px 12px;
  border: 1px solid color-mix(in srgb, var(--accent) 18%, var(--border));
  border-radius: var(--radius-sm);
  color: var(--text);
  background: color-mix(in srgb, var(--accent-bg) 42%, var(--bg));
  font-size: var(--font-sm);
}

.draft-hint.blocked {
  border-color: color-mix(in srgb, var(--danger, #b42318) 22%, var(--border));
  background: color-mix(in srgb, var(--danger, #b42318) 8%, var(--bg));
}

.citations {
  list-style: none;
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
}

.citations li {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
}

@media (max-width: 760px) {
  .question-row,
  .metric-grid {
    grid-template-columns: 1fr;
  }

  .head {
    display: grid;
  }
}
</style>
