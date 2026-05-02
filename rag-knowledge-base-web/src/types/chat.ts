// Chat & Settings Types

export interface ChatMessage {
  role: 'user' | 'assistant' | 'system'
  content: string
  sources?: SourceInfo[]
  timestamp: number
  loading?: boolean
  // Thinking block fields (for streaming LLM output with <think/> tags)
  thinking?: string
  isThinking?: boolean
  thinkingExpanded?: boolean
  // Retrieval status feedback
  status?: { phase: string; message: string; segments?: number }
  // Retrieved article count during streaming (before detailed sources are shown)
  sourceCount?: number
  // UI state
  _copied?: boolean
}

export interface SourceInfo {
  index: number
  text: string
  source: string
  rrfScore: number
  vectorScore: number
  breadcrumb?: string
  confidence: number
  confidenceLabel: string
  explanation: string
}

export interface RagAnswer {
  answer: string
  sources: SourceInfo[]
  references: string[]
  conversationId?: string
}

export interface Conversation {
  id: string
  title: string
  agentId: string | null
  createdAt: number
  updatedAt: number
}

export interface Agent {
  id: string
  name: string
  description: string
  systemPrompt: string
  avatar: string
  isDefault: boolean
  createdAt: number
  updatedAt: number
}

export interface LlmConfig {
  provider: string
  baseUrl: string
  apiKey: string
  modelName: string
  temperature: number | null
  maxTokens: number | null
  streaming: boolean
}

export interface EmbeddingConfig {
  provider: string
  baseUrl: string
  apiKey: string
  modelName: string
}

export interface VectorStoreConfig {
  type: string
  chromaBaseUrl: string
  collectionName: string
  milvusHost: string
  milvusPort: number
  embeddingDimension: number
}

export interface RagConfig {
  chunkSize: number
  chunkOverlap: number
  vectorTopK: number
  keywordTopK: number
  rrfK: number
  minScore: number
}

export interface A2AAppConfig {
  enabled: boolean
  agentName: string
  agentDescription: string
}

export interface DocumentTypeConfig {
  name: string
  label: string
  chunkSize: number
  chunkOverlap: number
}

export interface WebSearchConfig {
  provider: string
  apiKey: string
  baseUrl: string
  maxResults: number
}

export interface AppSettings {
  llm: LlmConfig
  embedding: EmbeddingConfig
  vectorStore: VectorStoreConfig
  rag: RagConfig
  a2a: A2AAppConfig
  documentTypes?: DocumentTypeConfig[]
  webSearch?: WebSearchConfig
}

// Memory Types
export interface MemoryEntry {
  id: string
  conversationId: string
  summary: string
  importance: number
  accessCount: number
  createdAt: number
  lastAccessedAt: number
  decayedImportance: number
}

// Eval Types
export interface EvalTestCase {
  id: string
  question: string
  relevantSources: string[]
  category: string
}

export interface EvalResult {
  timestamp: string
  totalCases: number
  topK: number
  hitRate: number
  avgRecallAtK: number
  avgPrecisionAtK: number
  mrr: number
  caseResults: EvalCaseSummary[]
}

export interface EvalCaseSummary {
  id: string
  question: string
  category: string
  hit: boolean
  topScore: number
}

// LLM Observability Types
export interface LlmCall {
  id: string
  callType: string
  model: string
  inputTokens: number | null
  outputTokens: number | null
  durationMs: number | null
  finishReason: string | null
  error: string | null
  createdAt: number
}

export interface LlmStats {
  totalCalls: number
  totalInputTokens: number
  totalOutputTokens: number
  avgDurationMs: number
}

export interface LlmDailyStat {
  dayStart: number
  calls: number
  inputTokens: number
  outputTokens: number
  avgDurationMs: number
}

export interface LlmHourlyStat {
  hour: number
  calls: number
  inputTokens: number
  outputTokens: number
  avgDurationMs: number
}

export interface LlmTypeCount {
  callType: string
  count: number
}

export interface LlmLatencyBucket {
  range: string
  count: number
}

export interface KnowledgeStats {
  documentCount: number
  segmentCount: number
  llmProvider: string
  llmModel: string
  embeddingProvider: string
  embeddingModel: string
  vectorStoreType: string
}

export interface HealthStatus {
  status: string
  documents: number
  segments: number
  llm: string
  embedding: string
}

// Provider presets for the settings page
export const PROVIDER_PRESETS: Record<string, Partial<LlmConfig>> = {
  ollama: {
    provider: 'ollama',
    baseUrl: 'http://localhost:11434/v1',
    apiKey: 'ollama',
    modelName: 'qwen2.5',
  },
  minimax: {
    provider: 'minimax',
    baseUrl: 'https://api.minimax.chat/v1',
    apiKey: '',
    modelName: 'MiniMax-M2.5',
  },
  openai: {
    provider: 'openai',
    baseUrl: 'https://api.openai.com/v1',
    apiKey: '',
    modelName: 'gpt-4o-mini',
  },
  custom: {
    provider: 'custom',
    baseUrl: '',
    apiKey: '',
    modelName: '',
  },
}

export const EMBEDDING_PRESETS: Record<string, Partial<EmbeddingConfig>> = {
  ollama: {
    provider: 'ollama',
    baseUrl: 'http://localhost:11434/v1',
    apiKey: 'ollama',
    modelName: 'nomic-embed-text',
  },
  zhipu: {
    provider: 'zhipu',
    baseUrl: 'https://open.bigmodel.cn/api/paas/v4',
    apiKey: '',
    modelName: 'embedding-3',
  },
  openai: {
    provider: 'openai',
    baseUrl: 'https://api.openai.com/v1',
    apiKey: '',
    modelName: 'text-embedding-3-small',
  },
  custom: {
    provider: 'custom',
    baseUrl: '',
    apiKey: '',
    modelName: '',
  },
}
