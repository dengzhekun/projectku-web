<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import CustomerServiceLogs from '../components/kb/CustomerServiceLogs.vue'
import KbChunkPreview from '../components/kb/KbChunkPreview.vue'
import KbDocumentForm from '../components/kb/KbDocumentForm.vue'
import KbHitLogs from '../components/kb/KbHitLogs.vue'
import KbIndexRecords from '../components/kb/KbIndexRecords.vue'
import KbMissLogs from '../components/kb/KbMissLogs.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiEmptyState from '../components/ui/UiEmptyState.vue'
import UiErrorPanel from '../components/ui/UiErrorPanel.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import {
  batchIndexKbDocuments,
  chunkKbDocument,
  createKbDocument,
  deleteKbDocument,
  fetchCustomerServiceLogs,
  fetchKbChunks,
  fetchKbDocument,
  fetchKbDocuments,
  fetchKbHitLogs,
  fetchKbIndexRecords,
  fetchKbMissLogs,
  fetchKbSyncHealth,
  indexKbDocument,
  type CustomerServiceLog,
  type KbBatchIndexItem,
  type KbChunk,
  type KbDocument,
  type KbHitLog,
  type KbIndexRecord,
  type KbMissLog,
  type KbSyncHealth,
  type KbSyncHealthItem,
  updateKbDocument,
  uploadKbDocument,
} from '../lib/knowledgeBase'
import { useToastStore } from '../stores/toast'
import { useAdminAuthStore } from '../stores/adminAuth'

const toast = useToastStore()
const router = useRouter()
const adminAuth = useAdminAuthStore()

const loading = ref(false)
const saving = ref(false)
const actionId = ref<number | null>(null)
const loadError = ref('')
const batchSyncing = ref(false)
const batchAllowLarge = ref(false)
const batchIncludeIndexed = ref(false)
const batchRecoverMapping = ref(false)
const batchLimitText = ref('')
const batchSummary = ref('')
const missesLoading = ref(false)
const csLogsLoading = ref(false)
const syncHealthLoading = ref(false)
const missStatus = ref('open')
const missKeyword = ref('')
const csLogRoute = ref('')
const csLogSourceType = ref('')
const csLogKeyword = ref('')

const filters = reactive({
  category: '',
  status: '',
  keyword: '',
})

const documents = ref<KbDocument[]>([])
const selectedId = ref<number | null>(null)
const selectedDocument = ref<KbDocument | null>(null)
const chunks = ref<KbChunk[]>([])
const records = ref<KbIndexRecord[]>([])
const hits = ref<KbHitLog[]>([])
const misses = ref<KbMissLog[]>([])
const customerServiceLogs = ref<CustomerServiceLog[]>([])
const syncHealth = ref<KbSyncHealth | null>(null)

const editForm = reactive({
  title: '',
  category: '',
  contentText: '',
})

const bindEditForm = (doc: KbDocument | null) => {
  editForm.title = doc?.title || ''
  editForm.category = doc?.category || ''
  editForm.contentText = doc?.contentText || ''
}

const loadRelated = async (id: number) => {
  const [detail, nextChunks, nextRecords, nextHits] = await Promise.all([
    fetchKbDocument(id),
    fetchKbChunks(id),
    fetchKbIndexRecords(id),
    fetchKbHitLogs(id),
  ])
  selectedDocument.value = detail
  selectedId.value = id
  chunks.value = nextChunks
  records.value = nextRecords
  hits.value = nextHits
  bindEditForm(detail)
}

const loadDocuments = async (preferredId?: number | null) => {
  loading.value = true
  loadError.value = ''
  try {
    documents.value = await fetchKbDocuments({
      category: filters.category || undefined,
      status: filters.status || undefined,
      keyword: filters.keyword || undefined,
    })

    const targetId =
      preferredId && documents.value.some((doc) => doc.id === preferredId)
        ? preferredId
        : documents.value[0]?.id ?? null

    if (targetId) {
      await loadRelated(targetId)
    } else {
      selectedId.value = null
      selectedDocument.value = null
      chunks.value = []
      records.value = []
      hits.value = []
      bindEditForm(null)
    }
  } catch (error: any) {
    loadError.value = error?.message || '加载知识库失败'
  } finally {
    loading.value = false
  }
}

const loadMisses = async () => {
  missesLoading.value = true
  try {
    misses.value = await fetchKbMissLogs({
      status: missStatus.value || undefined,
      keyword: missKeyword.value || undefined,
    })
  } catch (error: any) {
    toast.push({ type: 'error', message: error?.message || '加载未命中问题失败' })
  } finally {
    missesLoading.value = false
  }
}

const loadCustomerServiceLogs = async () => {
  csLogsLoading.value = true
  try {
    customerServiceLogs.value = await fetchCustomerServiceLogs({
      route: csLogRoute.value || undefined,
      sourceType: csLogSourceType.value || undefined,
      keyword: csLogKeyword.value || undefined,
    })
  } catch (error: any) {
    toast.push({ type: 'error', message: error?.message || '加载客服查询日志失败' })
  } finally {
    csLogsLoading.value = false
  }
}

const loadSyncHealth = async () => {
  syncHealthLoading.value = true
  try {
    syncHealth.value = await fetchKbSyncHealth()
  } catch (error: any) {
    toast.push({ type: 'error', message: error?.message || '加载同步健康失败' })
  } finally {
    syncHealthLoading.value = false
  }
}

const refreshDocumentsAndHealth = async (preferredId?: number | null) => {
  await loadDocuments(preferredId)
  await loadSyncHealth()
}

const handleCreateManual = async (payload: { title: string; category: string; contentText: string }) => {
  saving.value = true
  try {
    const created = await createKbDocument(payload)
    toast.push({ type: 'success', message: '文档已创建' })
    await refreshDocumentsAndHealth(created.id)
  } catch (error: any) {
    toast.push({ type: 'error', message: error?.message || '创建失败' })
  } finally {
    saving.value = false
  }
}

const handleUpload = async (payload: { title: string; category: string; file: File }) => {
  saving.value = true
  try {
    const created = await uploadKbDocument(payload)
    toast.push({ type: 'success', message: '文档已上传并解析' })
    await refreshDocumentsAndHealth(created.id)
  } catch (error: any) {
    toast.push({ type: 'error', message: error?.message || '上传失败' })
  } finally {
    saving.value = false
  }
}

const handleSaveDocument = async () => {
  if (!selectedId.value) return
  saving.value = true
  try {
    await updateKbDocument(selectedId.value, {
      title: editForm.title,
      category: editForm.category,
      contentText: editForm.contentText,
    })
    toast.push({ type: 'success', message: '文档已更新' })
    await refreshDocumentsAndHealth(selectedId.value)
  } catch (error: any) {
    toast.push({ type: 'error', message: error?.message || '更新失败' })
  } finally {
    saving.value = false
  }
}

const runAction = async (id: number, task: () => Promise<void>, successText: string) => {
  actionId.value = id
  try {
    await task()
    toast.push({ type: 'success', message: successText })
    await refreshDocumentsAndHealth(id)
  } catch (error: any) {
    toast.push({ type: 'error', message: error?.message || '操作失败' })
  } finally {
    actionId.value = null
  }
}

const handleDelete = async (id: number) => {
  if (!window.confirm('确认删除该知识文档吗？')) return
  actionId.value = id
  try {
    await deleteKbDocument(id)
    toast.push({ type: 'success', message: '文档已删除' })
    await refreshDocumentsAndHealth(selectedId.value === id ? null : selectedId.value)
  } catch (error: any) {
    toast.push({ type: 'error', message: error?.message || '删除失败' })
  } finally {
    actionId.value = null
  }
}

const parseLimit = (value: string) => {
  const normalized = value.trim()
  if (!normalized) return undefined
  const num = Number(normalized)
  if (!Number.isInteger(num) || num <= 0) return null
  return num
}

const summarizeBatchResult = (items: KbBatchIndexItem[]) => {
  if (!items.length) return '没有可同步的 chunked 文档'
  const success = items.filter((item) => item.result === 'success').length
  const failed = items.filter((item) => item.result === 'failed').length
  const skipped = items.filter((item) => item.result === 'skipped' || item.action === 'skip').length
  return `总数 ${items.length}，成功 ${success}，跳过 ${skipped}，失败 ${failed}`
}

const batchHint = computed(() => {
  const notes = ['默认只同步当前 chunked 文档']
  if (batchIncludeIndexed.value) {
    notes.push('会额外包含已索引文档')
  }
  if (batchRecoverMapping.value) {
    notes.push('会显式请求恢复旧映射')
  }
  if (batchAllowLarge.value) {
    notes.push('会放开超大文档阈值')
  }
  return notes.join('，')
})

const syncHealthItemsById = computed<Record<number, KbSyncHealthItem>>(() => {
  const entries = (syncHealth.value?.items ?? []).map((item) => [item.id, item] as const)
  return Object.fromEntries(entries)
})

const healthSummaryCards = computed(() => {
  const health = syncHealth.value
  return [
    { label: '总文档', value: health?.totalDocuments ?? '-' },
    { label: '已索引', value: health?.indexedDocuments ?? '-' },
    { label: '需同步', value: health?.needsSyncDocuments ?? '-' },
    {
      label: '失败 / 最新索引失败',
      value: health ? `${health.failedDocuments} / ${health.latestFailedIndexDocuments}` : '-',
    },
  ]
})

const getSyncHealthItem = (documentId: number) => syncHealthItemsById.value[documentId]

const handleBatchSync = async () => {
  const limit = parseLimit(batchLimitText.value)
  if (limit === null) {
    toast.push({ type: 'error', message: 'limit 需为正整数' })
    return
  }
  batchSyncing.value = true
  try {
    const result = await batchIndexKbDocuments({
      allowLarge: batchAllowLarge.value ? true : undefined,
      limit,
      includeIndexed: batchIncludeIndexed.value ? true : undefined,
      recoverMapping: batchRecoverMapping.value ? true : undefined,
    })
    const summary = summarizeBatchResult(result)
    batchSummary.value = `最近批量同步：${summary}`
    toast.push({ type: 'success', message: `批量同步完成：${summary}` })
    await refreshDocumentsAndHealth(selectedId.value)
  } catch (error: any) {
    toast.push({ type: 'error', message: error?.message || '批量同步失败' })
  } finally {
    batchSyncing.value = false
  }
}

const formatTime = (value?: string | null) => {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

const handleAdminLogout = async () => {
  adminAuth.logout()
  toast.push({ type: 'info', message: '已退出管理员后台' })
  await router.replace({ name: 'adminLogin' })
}

const handleFilterSearch = () => {
  refreshDocumentsAndHealth(selectedId.value)
}

const handleFilterReset = () => {
  filters.category = ''
  filters.status = ''
  filters.keyword = ''
  refreshDocumentsAndHealth(selectedId.value)
}

onMounted(() => {
  loadDocuments().then(loadSyncHealth)
  loadMisses()
  loadCustomerServiceLogs()
})
</script>

<template>
  <div class="page">
    <UiPageHeader title="知识库管理" :show-back="false">
      <template #right>
        <UiButton size="sm" @click="handleAdminLogout">退出后台</UiButton>
      </template>
    </UiPageHeader>

    <main class="main">
      <section class="filters panel">
        <div class="filter-grid">
          <UiInput v-model="filters.category" placeholder="按分类筛选" />
          <UiInput v-model="filters.status" placeholder="按状态筛选" />
          <UiInput v-model="filters.keyword" placeholder="按标题或内容搜索" />
        </div>
        <div class="actions">
          <UiButton variant="primary" :loading="loading" @click="handleFilterSearch">查询</UiButton>
          <UiButton @click="handleFilterReset">重置</UiButton>
        </div>
      </section>

      <section class="panel batch-panel">
        <div class="batch-controls">
          <UiButton size="sm" variant="primary" :loading="batchSyncing" @click="handleBatchSync">批量同步到 LightRAG</UiButton>
          <UiButton size="sm" :loading="syncHealthLoading" @click="loadSyncHealth">刷新健康</UiButton>
          <label class="batch-check">
            <input v-model="batchAllowLarge" type="checkbox" :disabled="batchSyncing" />
            <span>允许超大文档</span>
          </label>
          <label class="batch-check">
            <input v-model="batchIncludeIndexed" type="checkbox" :disabled="batchSyncing" />
            <span>包含已索引文档</span>
          </label>
          <label class="batch-check">
            <input v-model="batchRecoverMapping" type="checkbox" :disabled="batchSyncing" />
            <span>强制恢复旧映射</span>
          </label>
          <div class="batch-limit">
            <UiInput
              v-model="batchLimitText"
              type="number"
              inputmode="numeric"
              placeholder="limit（可选）"
              :disabled="batchSyncing"
            />
          </div>
        </div>
        <div class="health-grid">
          <div v-for="card in healthSummaryCards" :key="card.label" class="health-card">
            <span class="health-label">{{ card.label }}</span>
            <strong class="health-value">{{ card.value }}</strong>
          </div>
        </div>
        <p class="batch-hint">{{ batchHint }}</p>
        <p v-if="batchSummary" class="batch-summary">{{ batchSummary }}</p>
      </section>

      <KbDocumentForm :loading="saving" @create-manual="handleCreateManual" @upload="handleUpload" />

      <UiErrorPanel v-if="loadError" :desc="loadError" @action="loadDocuments(selectedId)" />

      <section class="layout">
        <article class="panel">
          <div class="section-head">
            <h2>文档列表</h2>
            <span>{{ documents.length }} 条</span>
          </div>

          <div v-if="loading" class="muted">正在加载…</div>
          <UiEmptyState
            v-else-if="!documents.length"
            title="暂无知识文档"
            desc="先创建一个手工文档，或上传一份 txt / md / docx。"
          />
          <ul v-else class="doc-list">
            <li
              v-for="doc in documents"
              :key="doc.id"
              class="doc-item"
              :class="{ active: doc.id === selectedId }"
              @click="loadRelated(doc.id)"
            >
              <div class="doc-title-row">
                <strong>{{ doc.title }}</strong>
                <span class="badge">{{ doc.status }}</span>
                <span class="sync-badge" :class="{ visible: !!getSyncHealthItem(doc.id)?.needsSync }">需同步</span>
              </div>
              <div class="doc-meta">
                <span>{{ doc.category }}</span>
                <span>v{{ doc.version }}</span>
                <span>{{ doc.sourceType || 'manual' }}</span>
                <span v-if="getSyncHealthItem(doc.id)?.latestIndexStatus === 'failed'" class="doc-meta-warn">最新索引失败</span>
              </div>
              <div class="doc-actions">
                <UiButton size="sm" :loading="actionId === doc.id" @click.stop="runAction(doc.id, () => chunkKbDocument(doc.id), '切分完成')">
                  切分
                </UiButton>
                <UiButton size="sm" variant="primary" :loading="actionId === doc.id" @click.stop="runAction(doc.id, () => indexKbDocument(doc.id), '索引完成')">
                  索引
                </UiButton>
                <UiButton size="sm" variant="danger" :loading="actionId === doc.id" @click.stop="handleDelete(doc.id)">
                  删除
                </UiButton>
              </div>
            </li>
          </ul>
        </article>

        <article class="panel detail-panel">
          <div class="section-head">
            <h2>文档详情</h2>
            <span v-if="selectedDocument">ID {{ selectedDocument.id }}</span>
          </div>

          <UiEmptyState
            v-if="!selectedDocument"
            title="未选择文档"
            desc="从左侧选择文档后，可以编辑内容、查看切分与索引状态。"
          />
          <div v-else class="detail">
            <div class="detail-grid">
              <UiInput v-model="editForm.title" placeholder="标题" />
              <UiInput v-model="editForm.category" placeholder="分类" />
            </div>
            <textarea v-model="editForm.contentText" class="textarea" rows="10" placeholder="文档正文" />
            <div class="info-grid">
              <div><strong>状态：</strong>{{ selectedDocument.status }}</div>
              <div><strong>版本：</strong>v{{ selectedDocument.version }}</div>
              <div><strong>来源：</strong>{{ selectedDocument.sourceType || 'manual' }}</div>
              <div><strong>创建人：</strong>{{ selectedDocument.createdBy || '-' }}</div>
              <div><strong>创建时间：</strong>{{ formatTime(selectedDocument.createdAt) }}</div>
              <div><strong>更新时间：</strong>{{ formatTime(selectedDocument.updatedAt) }}</div>
            </div>
            <div class="actions">
              <UiButton variant="primary" :loading="saving" @click="handleSaveDocument">保存修改</UiButton>
              <UiButton :loading="actionId === selectedDocument!.id" @click="runAction(selectedDocument!.id, () => chunkKbDocument(selectedDocument!.id), '切分完成')">
                重新切分
              </UiButton>
              <UiButton variant="primary" :loading="actionId === selectedDocument!.id" @click="runAction(selectedDocument!.id, () => indexKbDocument(selectedDocument!.id), '索引完成')">
                重新索引
              </UiButton>
              <UiButton :loading="actionId === selectedDocument!.id" @click="runAction(selectedDocument!.id, () => indexKbDocument(selectedDocument!.id, { recoverMapping: true }), '修复重建完成')">
                修复索引
              </UiButton>
            </div>
          </div>
        </article>
      </section>

      <section class="secondary-grid">
        <KbChunkPreview :chunks="chunks" />
        <KbIndexRecords :records="records" />
      </section>

      <KbHitLogs :hits="hits" />

      <CustomerServiceLogs :logs="customerServiceLogs" :loading="csLogsLoading">
        <template #actions>
          <div class="miss-actions">
            <UiInput v-model="csLogRoute" placeholder="路由" :disabled="csLogsLoading" />
            <UiInput v-model="csLogSourceType" placeholder="来源" :disabled="csLogsLoading" />
            <UiInput v-model="csLogKeyword" placeholder="搜索问题" :disabled="csLogsLoading" />
            <UiButton size="sm" :loading="csLogsLoading" @click="loadCustomerServiceLogs">刷新</UiButton>
          </div>
        </template>
      </CustomerServiceLogs>

      <KbMissLogs :misses="misses" :loading="missesLoading">
        <template #actions>
          <div class="miss-actions">
            <UiInput v-model="missStatus" placeholder="状态" :disabled="missesLoading" />
            <UiInput v-model="missKeyword" placeholder="搜索问题" :disabled="missesLoading" />
            <UiButton size="sm" :loading="missesLoading" @click="loadMisses">刷新</UiButton>
          </div>
        </template>
      </KbMissLogs>
    </main>
  </div>
</template>

<style scoped>
.page {
  min-height: 100svh;
  background: var(--code-bg);
}

.main {
  display: grid;
  gap: 16px;
  padding: 20px 16px 40px;
}

.panel {
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--bg);
  box-shadow: var(--shadow);
  padding: 18px;
}

.batch-panel {
  padding-top: 14px;
  padding-bottom: 14px;
}

.batch-controls {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px 12px;
}

.batch-check {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--text);
  font-size: var(--font-sm);
}

.batch-check input {
  margin: 0;
}

.batch-limit {
  width: 150px;
}

.batch-summary {
  margin: 8px 0 0;
  color: var(--text);
  font-size: var(--font-sm);
}

.health-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-top: 12px;
}

.health-card {
  display: grid;
  gap: 4px;
  min-height: 68px;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--bg) 92%, var(--accent-bg));
}

.health-label {
  color: var(--text);
  font-size: var(--font-sm);
  opacity: 0.8;
}

.health-value {
  color: var(--text-h);
  font-size: 24px;
  line-height: 1.1;
}

.batch-hint {
  margin: 8px 0 0;
  color: var(--text);
  opacity: 0.82;
  font-size: var(--font-sm);
}

.miss-actions {
  display: grid;
  grid-template-columns: 120px 120px minmax(160px, 240px) auto;
  align-items: center;
  gap: 8px;
}

.filters,
.actions,
.detail,
.detail-grid,
.filter-grid {
  display: grid;
  gap: 12px;
}

.layout,
.secondary-grid {
  display: grid;
  gap: 16px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.section-head h2 {
  margin: 0;
  font-size: 20px;
}

.muted {
  color: var(--text);
  font-size: var(--font-sm);
}

.doc-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 12px;
}

.doc-item {
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 14px;
  cursor: pointer;
  display: grid;
  gap: 10px;
}

.doc-item.active {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-bg);
}

.doc-title-row,
.doc-meta,
.doc-actions,
.info-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.doc-title-row strong,
.detail strong {
  color: var(--text-h);
}

.doc-meta,
.info-grid {
  color: var(--text);
  font-size: var(--font-sm);
}

.badge {
  padding: 2px 10px;
  border-radius: var(--radius-pill);
  background: var(--accent-bg);
  color: var(--accent);
  font-size: var(--font-sm);
}

.sync-badge {
  min-width: 52px;
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  border: 1px solid transparent;
  color: transparent;
  font-size: 12px;
  line-height: 1.3;
  visibility: hidden;
}

.sync-badge.visible {
  border-color: color-mix(in srgb, var(--warning, #c97a00) 18%, transparent);
  background: color-mix(in srgb, var(--warning, #c97a00) 12%, white);
  color: var(--warning, #8f5200);
  visibility: visible;
}

.doc-meta-warn {
  color: var(--danger, #b42318);
}

.textarea {
  width: 100%;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 12px;
  font: inherit;
  color: var(--text-h);
  background: var(--bg);
  resize: vertical;
}

@media (min-width: 1100px) {
  .layout {
    grid-template-columns: 420px minmax(0, 1fr);
  }

  .secondary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .filter-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .detail-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .batch-limit {
    width: 100%;
  }

  .health-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .miss-actions {
    grid-template-columns: 1fr;
  }
}
</style>
