import { api } from './api'

export type KbDocument = {
  id: number
  title: string
  category: string
  sourceType?: string | null
  status: string
  version: number
  storagePath?: string | null
  contentText: string
  createdBy?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export type KbChunk = {
  id: number
  chunkIndex: number
  content: string
  charCount: number
  status?: string | null
}

export type KbIndexRecord = {
  id: number
  version: number
  embeddingProvider?: string | null
  vectorCollection?: string | null
  indexedChunkCount: number
  status: string
  errorMessage?: string | null
  createdAt?: string | null
}

export type KbHitLog = {
  id: number
  documentId: number
  chunkId: number
  queryText: string
  conversationId?: string | null
  hitTime?: string | null
}

export type KbMissLog = {
  id: number
  queryText: string
  conversationId?: string | null
  confidence?: number | null
  fallbackReason?: string | null
  status: string
  createdAt?: string | null
}

export type CustomerServiceLog = {
  id: number
  queryText: string
  conversationId?: string | null
  route: string
  sourceType: string
  sourceId?: string | null
  confidence?: number | null
  fallbackReason?: string | null
  createdAt?: string | null
}

type ApiEnvelope<T> = {
  code: number
  message: string
  data: T
}

export type KbBatchIndexItem = {
  documentId: number
  title?: string | null
  status?: string | null
  chunkCount: number
  action: string
  result: string
  error?: string | null
}

export type KbSyncHealthItem = {
  id: number
  title: string
  category: string
  status: string
  version: number
  chunkCount: number
  latestIndexStatus?: string | null
  latestIndexedChunkCount: number
  latestIndexError?: string | null
  needsSync: boolean
}

export type KbSyncHealth = {
  totalDocuments: number
  parsedDocuments: number
  chunkedDocuments: number
  indexedDocuments: number
  failedDocuments: number
  needsSyncDocuments: number
  staleDocuments: number
  missingChunkDocuments: number
  latestFailedIndexDocuments: number
  items: KbSyncHealthItem[]
}

export const fetchKbDocuments = async (params?: {
  category?: string
  status?: string
  keyword?: string
}) => {
  const res = await api.get<ApiEnvelope<KbDocument[]>>('/v1/kb/documents', { params })
  return res.data.data ?? []
}

export const fetchKbSyncHealth = async () => {
  const res = await api.get<ApiEnvelope<KbSyncHealth>>('/v1/kb/documents/sync-health')
  return res.data.data
}

export const fetchKbDocument = async (id: number) => {
  const res = await api.get<ApiEnvelope<KbDocument>>(`/v1/kb/documents/${id}`)
  return res.data.data
}

export const createKbDocument = async (payload: { title: string; category: string; contentText: string }) => {
  const res = await api.post<ApiEnvelope<KbDocument>>('/v1/kb/documents', payload)
  return res.data.data
}

export const updateKbDocument = async (id: number, payload: { title: string; category: string; contentText: string }) => {
  const res = await api.put<ApiEnvelope<KbDocument>>(`/v1/kb/documents/${id}`, payload)
  return res.data.data
}

export const deleteKbDocument = async (id: number) => {
  await api.delete(`/v1/kb/documents/${id}`)
}

export const uploadKbDocument = async (payload: { title: string; category: string; file: File }) => {
  const formData = new FormData()
  formData.append('title', payload.title)
  formData.append('category', payload.category)
  formData.append('file', payload.file)
  const res = await api.post<ApiEnvelope<KbDocument>>('/v1/kb/documents/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return res.data.data
}

export const chunkKbDocument = async (id: number) => {
  await api.post(`/v1/kb/documents/${id}/chunk`)
}

export const fetchKbChunks = async (id: number) => {
  const res = await api.get<ApiEnvelope<KbChunk[]>>(`/v1/kb/documents/${id}/chunks`)
  return res.data.data ?? []
}

export const indexKbDocument = async (id: number, params?: { recoverMapping?: boolean }) => {
  const query: { recoverMapping?: boolean } = {}
  if (params?.recoverMapping !== undefined) query.recoverMapping = params.recoverMapping
  await api.post(`/v1/kb/documents/${id}/index`, null, { params: query })
}

export const batchIndexKbDocuments = async (params?: {
  allowLarge?: boolean
  limit?: number
  includeIndexed?: boolean
  recoverMapping?: boolean
}) => {
  const query: { allowLarge?: boolean; limit?: number; includeIndexed?: boolean; recoverMapping?: boolean } = {}
  if (params?.allowLarge !== undefined) query.allowLarge = params.allowLarge
  if (params?.limit !== undefined) query.limit = params.limit
  if (params?.includeIndexed !== undefined) query.includeIndexed = params.includeIndexed
  if (params?.recoverMapping !== undefined) query.recoverMapping = params.recoverMapping
  const res = await api.post<ApiEnvelope<KbBatchIndexItem[]>>('/v1/kb/documents/batch-index', null, { params: query })
  return res.data.data ?? []
}

export const fetchKbIndexRecords = async (id: number) => {
  const res = await api.get<ApiEnvelope<KbIndexRecord[]>>(`/v1/kb/documents/${id}/index-records`)
  return res.data.data ?? []
}

export const fetchKbHitLogs = async (id: number) => {
  const res = await api.get<ApiEnvelope<KbHitLog[]>>(`/v1/kb/documents/${id}/hits`)
  return res.data.data ?? []
}

export const fetchKbMissLogs = async (params?: { status?: string; keyword?: string }) => {
  const res = await api.get<ApiEnvelope<KbMissLog[]>>('/v1/kb/documents/misses', { params })
  return res.data.data ?? []
}

export const fetchCustomerServiceLogs = async (params?: {
  route?: string
  sourceType?: string
  keyword?: string
}) => {
  const res = await api.get<ApiEnvelope<CustomerServiceLog[]>>('/v1/kb/documents/customer-service-logs', { params })
  return res.data.data ?? []
}
