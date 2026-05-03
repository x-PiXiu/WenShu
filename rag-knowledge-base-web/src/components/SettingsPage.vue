<template>
  <div class="settings-page">
    <n-spin :show="loading">
      <!-- Top row: LLM + Embedding side by side -->
      <div class="grid-2col">
        <!-- LLM Config -->
        <div class="settings-card">
          <div class="card-header">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#E8913A" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2a3 3 0 0 0-3 3v1H4a2 2 0 0 0-2 2v4a2 2 0 0 0 2 2h1v5a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-5h1a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-5V5a3 3 0 0 0-3-3z"/></svg>
            <span class="card-title">LLM 大语言模型</span>
          </div>
          <n-form label-placement="left" label-width="85">
            <n-form-item label="Provider">
              <n-select
                :value="form.llm.provider"
                :options="providerOptions"
                @update:value="(v: string) => onProviderChange('llm', v)"
              />
              <template #feedback>
                <span class="field-hint">选择 LLM 服务商，切换后自动填充 Base URL 和模型名</span>
              </template>
            </n-form-item>
            <n-form-item label="Base URL">
              <n-input v-model:value="form.llm.baseUrl" placeholder="http://localhost:11434/v1" />
              <template #feedback>
                <span class="field-hint">OpenAI 兼容的 API 地址，Ollama 为 http://localhost:11434/v1</span>
              </template>
            </n-form-item>
            <n-form-item label="API Key">
              <n-input v-model:value="form.llm.apiKey" type="password" show-password-on="click" placeholder="API Key" />
              <template #feedback>
                <span class="field-hint">服务商提供的认证密钥，Ollama 本地可填任意值</span>
              </template>
            </n-form-item>
            <n-form-item label="Model">
              <n-input v-model:value="form.llm.modelName" placeholder="qwen2.5" />
              <template #feedback>
                <span class="field-hint">模型标识，如 qwen2.5、MiniMax-M2.5、gpt-4o</span>
              </template>
            </n-form-item>
            <n-form-item label="Temperature">
              <n-slider v-model:value="llmTemp" :min="0" :max="2" :step="0.1" />
              <template #feedback>
                <span class="field-hint">控制回答的随机性，0 = 确定精准，2 = 发散创意</span>
              </template>
            </n-form-item>
            <n-form-item label="Max Tokens">
              <n-input-number v-model:value="llmTokens" :min="128" :max="32768" :step="256" />
              <template #feedback>
                <span class="field-hint">单次回答的最大输出长度，越长回答越详细但耗时更久</span>
              </template>
            </n-form-item>
            <n-form-item label="流式输出">
              <n-switch v-model:value="form.llm.streaming" />
              <template #feedback>
                <span class="field-hint">开启后逐字实时输出回答，支持思考过程展示（需模型支持）</span>
              </template>
            </n-form-item>
          </n-form>
        </div>

        <!-- Embedding Config -->
        <div class="settings-card">
          <div class="card-header">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#E8913A" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/></svg>
            <span class="card-title">Embedding 向量模型</span>
          </div>
          <n-form label-placement="left" label-width="85">
            <n-form-item label="Provider">
              <n-select
                :value="form.embedding.provider"
                :options="embeddingProviderOptions"
                @update:value="(v: string) => onProviderChange('embedding', v)"
              />
              <template #feedback>
                <span class="field-hint">文本向量化服务商，用于将文档转为语义向量</span>
              </template>
            </n-form-item>
            <n-form-item label="Base URL">
              <n-input v-model:value="form.embedding.baseUrl" placeholder="http://localhost:11434/v1" />
              <template #feedback>
                <span class="field-hint">向量模型 API 地址，与 LLM 可使用不同服务</span>
              </template>
            </n-form-item>
            <n-form-item label="API Key">
              <n-input v-model:value="form.embedding.apiKey" type="password" show-password-on="click" placeholder="API Key" />
              <template #feedback>
                <span class="field-hint">向量服务的认证密钥</span>
              </template>
            </n-form-item>
            <n-form-item label="Model">
              <n-input v-model:value="form.embedding.modelName" placeholder="nomic-embed-text" />
              <template #feedback>
                <span class="field-hint">如 nomic-embed-text、embedding-3、text-embedding-3-small</span>
              </template>
            </n-form-item>
          </n-form>
        </div>
      </div>

      <!-- Middle row: Vector Store + RAG Parameters side by side -->
      <div class="grid-2col">
        <!-- Vector Store Config -->
        <div class="settings-card">
          <div class="card-header">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#E8913A" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"/><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"/></svg>
            <span class="card-title">向量存储</span>
          </div>
          <n-form label-placement="left" label-width="100">
            <n-form-item label="Type">
              <n-select v-model:value="form.vectorStore.type" :options="vectorStoreOptions" />
              <template #feedback>
                <span class="field-hint">In-Memory 重启即丢失；Chroma/Milvus 支持持久化</span>
              </template>
            </n-form-item>
            <n-form-item v-if="form.vectorStore.type === 'chroma'" label="Chroma URL">
              <n-input v-model:value="form.vectorStore.chromaBaseUrl" placeholder="http://localhost:8000" />
              <template #feedback>
                <span class="field-hint">Chroma 服务地址，需提前启动 Chroma 实例</span>
              </template>
            </n-form-item>
            <n-form-item v-if="form.vectorStore.type === 'chroma'" label="Collection">
              <n-input v-model:value="form.vectorStore.collectionName" placeholder="rag_knowledge_base" />
              <template #feedback>
                <span class="field-hint">Chroma 中的集合名，用于隔离不同知识库</span>
              </template>
            </n-form-item>
            <n-form-item v-if="form.vectorStore.type === 'milvus'" label="Milvus Host">
              <n-input v-model:value="form.vectorStore.milvusHost" placeholder="localhost" />
              <template #feedback>
                <span class="field-hint">Milvus 服务 IP 地址</span>
              </template>
            </n-form-item>
            <n-form-item v-if="form.vectorStore.type === 'milvus'" label="Milvus Port">
              <n-input-number v-model:value="form.vectorStore.milvusPort" :min="1" :max="65535" />
              <template #feedback>
                <span class="field-hint">gRPC 端口，默认 19530</span>
              </template>
            </n-form-item>
            <n-form-item v-if="form.vectorStore.type === 'milvus'" label="Collection">
              <n-input v-model:value="form.vectorStore.collectionName" placeholder="rag_knowledge_base" />
              <template #feedback>
                <span class="field-hint">Milvus 中的 Collection 名称</span>
              </template>
            </n-form-item>
            <n-form-item v-if="form.vectorStore.type === 'milvus'" label="Dimension">
              <n-input-number v-model:value="form.vectorStore.embeddingDimension" :min="1" :max="4096" :step="64" />
              <template #feedback>
                <span class="field-hint">向量维度，必须与 Embedding 模型输出一致（如 nomic-embed-text=768）</span>
              </template>
            </n-form-item>
            <div v-if="form.vectorStore.type !== 'memory'" class="info-tip">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#D97B2B" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
              切换外部向量存储后需点击「重建索引」，确保存储服务已启动。
            </div>
          </n-form>
        </div>

        <!-- RAG Parameters -->
        <div class="settings-card">
          <div class="card-header">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#E8913A" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="4" y1="21" x2="4" y2="14"/><line x1="4" y1="10" x2="4" y2="3"/><line x1="12" y1="21" x2="12" y2="12"/><line x1="12" y1="8" x2="12" y2="3"/><line x1="20" y1="21" x2="20" y2="16"/><line x1="20" y1="12" x2="20" y2="3"/><line x1="1" y1="14" x2="7" y2="14"/><line x1="9" y1="8" x2="15" y2="8"/><line x1="17" y1="16" x2="23" y2="16"/></svg>
            <span class="card-title">RAG 检索参数</span>
          </div>
          <n-form label-placement="left" label-width="100">
            <n-form-item label="Chunk Size">
              <n-input-number v-model:value="form.rag.chunkSize" :min="50" :max="2000" :step="50" />
              <template #feedback>
                <span class="field-hint">文档分割的每段字符数，建议 200-500</span>
              </template>
            </n-form-item>
            <n-form-item label="Chunk Overlap">
              <n-input-number v-model:value="form.rag.chunkOverlap" :min="0" :max="500" :step="10" />
              <template #feedback>
                <span class="field-hint">相邻片段重叠字符数，防止语义断裂</span>
              </template>
            </n-form-item>
            <n-form-item label="Vector Top K">
              <n-input-number v-model:value="form.rag.vectorTopK" :min="1" :max="50" />
              <template #feedback>
                <span class="field-hint">语义向量检索返回的片段数，值越大召回越多</span>
              </template>
            </n-form-item>
            <n-form-item label="Keyword Top K">
              <n-input-number v-model:value="form.rag.keywordTopK" :min="1" :max="50" />
              <template #feedback>
                <span class="field-hint">关键词 BM25 检索返回的片段数</span>
              </template>
            </n-form-item>
            <n-form-item label="RRF K">
              <n-input-number v-model:value="form.rag.rrfK" :min="1" :max="100" />
              <template #feedback>
                <span class="field-hint">倒数排名融合常数，用于合并向量+关键词两组检索结果</span>
              </template>
            </n-form-item>
          </n-form>
        </div>
      </div>

      <!-- Document Type Chunk Settings (full width) -->
      <div class="settings-card" style="margin-bottom: 16px">
        <div class="card-header">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#E8913A" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
          <span class="card-title">文档类型分块策略</span>
        </div>
        <div class="doc-type-grid">
          <div v-for="(dt, idx) in form.documentTypes" :key="dt.name" class="doc-type-row">
            <div class="doc-type-name-group">
              <input
                class="doc-type-name-input"
                :value="dt.name"
                @input="onTypeNameChange(idx, ($event.target as HTMLInputElement).value)"
                placeholder="TYPE_NAME"
                :title="'类型标识: ' + dt.name"
              />
              <input
                class="doc-type-label-input"
                v-model="dt.label"
                placeholder="显示名称"
              />
            </div>
            <div class="doc-type-fields">
              <div class="field-pair">
                <label>Chunk Size</label>
                <n-input-number v-model:value="dt.chunkSize" :min="50" :max="4000" :step="50" size="small" />
              </div>
              <div class="field-pair">
                <label>Overlap</label>
                <n-input-number v-model:value="dt.chunkOverlap" :min="0" :max="1000" :step="10" size="small" />
              </div>
            </div>
            <button class="doc-type-delete" @click="removeDocType(idx)" title="删除此类型">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
            </button>
          </div>
        </div>
        <!-- Add new type -->
        <div class="doc-type-add-bar">
          <input
            class="doc-type-name-input new-name"
            v-model="newTypeName"
            placeholder="新类型标识 (英文)"
          />
          <input
            class="doc-type-label-input new-label"
            v-model="newTypeLabel"
            placeholder="显示名称 (中文)"
          />
          <button class="add-type-btn" @click="addDocType" :disabled="!newTypeName.trim() || !newTypeLabel.trim()">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            添加类型
          </button>
        </div>
        <div class="info-tip" style="margin: 0 18px 14px">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#D97B2B" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
          修改后需「重建索引」才能生效。已上传文档的类型不会自动变更，仅在重新上传时使用新参数。
        </div>
      </div>

      <!-- Web Search Config -->
      <div class="settings-card" style="margin-bottom: 16px">
        <h3 class="card-title">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          互联网搜索
          <span class="ws-status" :class="form.webSearch!.provider !== 'none' ? 'ws-on' : 'ws-off'">
            {{ form.webSearch!.provider !== 'none' ? '已启用' : '未启用' }}
          </span>
        </h3>
        <div class="ws-provider-grid">
          <label v-for="opt in webSearchProviders" :key="opt.value"
                 :class="['ws-provider-card', { active: form.webSearch!.provider === opt.value }]">
            <input type="radio" v-model="form.webSearch!.provider" :value="opt.value" />
            <div class="ws-provider-icon">{{ opt.icon }}</div>
            <div class="ws-provider-info">
              <div class="ws-provider-name">{{ opt.label }}</div>
              <div class="ws-provider-desc">{{ opt.desc }}</div>
            </div>
          </label>
        </div>

        <transition name="slide-fade">
          <div v-if="form.webSearch!.provider !== 'none'" class="ws-config-section">
            <div class="ws-config-row">
              <div class="ws-field">
                <label class="ws-label">API Key</label>
                <div class="ws-input-wrap">
                  <svg class="ws-input-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#888" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
                  <input :type="showWsKey ? 'text' : 'password'"
                         v-model="form.webSearch!.apiKey"
                         class="ws-input"
                         placeholder="输入 API Key" />
                  <button class="ws-toggle-vis" @click="showWsKey = !showWsKey" :title="showWsKey ? '隐藏' : '显示'">
                    <svg v-if="!showWsKey" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#888" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                    <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#888" stroke-width="2"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
                  </button>
                </div>
              </div>
              <div v-if="form.webSearch!.provider === 'custom'" class="ws-field">
                <label class="ws-label">API 地址</label>
                <div class="ws-input-wrap">
                  <svg class="ws-input-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#888" stroke-width="2"><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/></svg>
                  <input v-model="form.webSearch!.baseUrl" class="ws-input" placeholder="https://api.example.com/search?q={query}" />
                </div>
              </div>
              <div class="ws-field ws-field-sm">
                <label class="ws-label">最大结果数</label>
                <div class="ws-input-wrap">
                  <n-input-number v-model:value="form.webSearch!.maxResults" :min="1" :max="10" size="small" style="width: 100%" />
                </div>
              </div>
            </div>
          </div>
        </transition>

        <div class="info-tip" style="margin: 12px 0 0">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#D97B2B" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
          当知识库无相关结果时，Agent 将自动使用互联网搜索补充信息。推荐 Tavily（专为 AI 优化）。
        </div>
      </div>

      <!-- Prompt Template Management -->
      <div class="settings-card" style="margin-bottom: 16px">
        <div class="card-header" @click="promptExpanded = !promptExpanded" style="cursor: pointer">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#E8913A" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/></svg>
          <span class="card-title">Prompt 模板管理</span>
          <span class="prompt-count">{{ Object.keys(form.prompts || {}).length }} 个模板</span>
          <svg :class="['chevron', { expanded: promptExpanded }]" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#B8A898" stroke-width="2"><polyline points="6 9 12 15 18 9"/></svg>
        </div>
        <div v-if="promptExpanded" class="prompt-section">
          <div v-for="(cat, catIdx) in promptCategories" :key="cat.key" class="prompt-category">
            <div class="prompt-cat-header" @click="cat.expanded = !cat.expanded">
              <svg :class="['chevron', { expanded: cat.expanded }]" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#B8A898" stroke-width="2"><polyline points="6 9 12 15 18 9"/></svg>
              <span class="prompt-cat-label">{{ cat.label }}</span>
              <span class="prompt-cat-count">{{ cat.keys.length }}</span>
            </div>
            <div v-if="cat.expanded" class="prompt-items">
              <div v-for="key in cat.keys" :key="key" class="prompt-item">
                <div class="prompt-item-header">
                  <span class="prompt-item-desc">{{ form.prompts?.[key]?.description || key }}</span>
                  <button class="prompt-reset-btn" @click="resetPrompt(key)" title="恢复默认">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
                    重置
                  </button>
                </div>
                <textarea
                  class="prompt-textarea"
                  v-model="form.prompts![key].template"
                  rows="6"
                  :placeholder="'Prompt 模板内容...'"
                ></textarea>
                <div v-if="promptHasVars(key)" class="prompt-var-hint">
                  支持变量：<code v-for="v in promptVars(key)" :key="v">{{ v }}</code>
                </div>
              </div>
            </div>
          </div>
          <div class="info-tip">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#D97B2B" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
            修改后点击「保存设置」生效。变量使用 <code>${'{'}变量名{'}'}</code> 格式，如 <code>${'{'}cardCount{'}'}</code>。
          </div>
        </div>
      </div>

      <!-- Agent Persona Management -->
      <div class="settings-card" style="margin-bottom: 16px">
        <AgentManager />
      </div>

      <!-- Actions -->
      <div class="actions-bar">
        <button class="action-btn primary" :disabled="saving" @click="handleSave">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/><polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/></svg>
          <span>{{ saving ? '保存中...' : '保存设置' }}</span>
        </button>
        <button class="action-btn warning" :disabled="saving" @click="handleReindex">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
          <span>{{ saving ? '重建中...' : '重建索引' }}</span>
        </button>
      </div>

      <div v-if="message" :class="['toast-msg', message.includes('failed') || message.includes('Error') ? 'error' : 'success']">
        <svg v-if="!message.includes('failed') && !message.includes('Error')" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
        <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
        {{ message }}
      </div>
    </n-spin>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import {
  NForm, NFormItem, NInput, NInputNumber, NSelect, NSlider,
  NSpin, NSwitch,
} from 'naive-ui'
import { useSettings } from '../composables/useSettings'
import { PROVIDER_PRESETS, EMBEDDING_PRESETS } from '../types/chat'
import type { AppSettings, DocumentTypeConfig, PromptEntry } from '../types/chat'
import AgentManager from './AgentManager.vue'

const { settings, loading, saving, message, fetchSettings, saveSettings, reindex } = useSettings()

const form = reactive<AppSettings>({
  llm: { provider: 'ollama', baseUrl: 'http://localhost:11434/v1', apiKey: 'ollama', modelName: 'qwen2.5', temperature: 0.7, maxTokens: 2048, streaming: true },
  embedding: { provider: 'ollama', baseUrl: 'http://localhost:11434/v1', apiKey: 'ollama', modelName: 'nomic-embed-text' },
  vectorStore: { type: 'memory', chromaBaseUrl: 'http://localhost:8000', collectionName: 'rag_knowledge_base', milvusHost: 'localhost', milvusPort: 19530, embeddingDimension: 768 },
  rag: { chunkSize: 300, chunkOverlap: 30, vectorTopK: 5, keywordTopK: 10, rrfK: 60, minScore: 0.3 },
  a2a: { enabled: true, agentName: '文枢', agentDescription: '' },
  webSearch: { provider: 'none', apiKey: '', baseUrl: '', maxResults: 5 },
  prompts: {} as Record<string, PromptEntry>,
  documentTypes: [
    { name: 'GENERAL', label: '通用文档', chunkSize: 512, chunkOverlap: 50 },
    { name: 'TECHNICAL', label: '技术文档', chunkSize: 800, chunkOverlap: 100 },
    { name: 'FAQ', label: 'FAQ/问答对', chunkSize: 256, chunkOverlap: 20 },
    { name: 'LOG', label: '日志/结构化数据', chunkSize: 512, chunkOverlap: 50 },
    { name: 'ARTICLE', label: '长文/手册', chunkSize: 1024, chunkOverlap: 150 },
  ],
})

const llmTemp = ref(0.7)
const llmTokens = ref(2048)
const newTypeName = ref('')
const newTypeLabel = ref('')
const showWsKey = ref(false)
const promptExpanded = ref(false)
const promptDefaults = ref<Record<string, PromptEntry> | null>(null)

const promptCategories = ref([
  { key: 'core', label: '核心提示词', expanded: true, keys: ['rag_qa', 'blog_qa', 'flashcard_generate'] },
  { key: 'summary', label: '摘要提示词', expanded: false, keys: ['conversation_summary', 'article_summary'] },
  { key: 'tool', label: '工具提示词', expanded: false, keys: ['translate', 'document_compare', 'search_summary'] },
])

function promptHasVars(key: string): boolean {
  const tmpl = form.prompts?.[key]?.template || ''
  return /\$\{[^}]+\}/.test(tmpl)
}

function promptVars(key: string): string[] {
  const tmpl = form.prompts?.[key]?.template || ''
  return [...tmpl.matchAll(/\$\{([^}]+)\}/g)].map(m => m[1])
}

function resetPrompt(key: string) {
  if (promptDefaults.value?.[key]) {
    if (form.prompts) {
      form.prompts[key].template = promptDefaults.value[key].template
    }
  }
}

const webSearchProviders = [
  { value: 'none', label: '关闭', desc: '不使用互联网搜索', icon: '⊘' },
  { value: 'tavily', label: 'Tavily', desc: '专为 AI 优化，推荐', icon: '🔍' },
  { value: 'serpapi', label: 'SerpAPI', desc: 'Google 搜索 API', icon: '🌐' },
  { value: 'custom', label: '自定义', desc: '自定义搜索 API', icon: '⚙' },
]

const providerOptions = [
  { label: 'Ollama (Local)', value: 'ollama' },
  { label: 'MiniMax', value: 'minimax' },
  { label: 'OpenAI', value: 'openai' },
  { label: 'Custom', value: 'custom' },
]

const embeddingProviderOptions = [
  { label: 'Ollama (Local)', value: 'ollama' },
  { label: 'ZhiPu (GLM)', value: 'zhipu' },
  { label: 'OpenAI', value: 'openai' },
  { label: 'Custom', value: 'custom' },
]

const vectorStoreOptions = [
  { label: 'In-Memory (Default)', value: 'memory' },
  { label: 'Chroma', value: 'chroma' },
  { label: 'Milvus', value: 'milvus' },
]

function onProviderChange(type: 'llm' | 'embedding', provider: string) {
  if (type === 'llm') {
    const preset = PROVIDER_PRESETS[provider]
    if (preset) {
      form.llm.provider = preset.provider!
      form.llm.baseUrl = preset.baseUrl!
      form.llm.modelName = preset.modelName!
      if (preset.apiKey !== undefined) form.llm.apiKey = preset.apiKey
    }
  } else {
    const preset = EMBEDDING_PRESETS[provider]
    if (preset) {
      form.embedding.provider = preset.provider!
      form.embedding.baseUrl = preset.baseUrl!
      form.embedding.modelName = preset.modelName!
      if (preset.apiKey !== undefined) form.embedding.apiKey = preset.apiKey
    }
  }
}

function onTypeNameChange(idx: number, newName: string) {
  const upper = newName.toUpperCase().replace(/[^A-Z0-9_]/g, '')
  form.documentTypes![idx].name = upper
}

function addDocType() {
  const name = newTypeName.value.trim().toUpperCase().replace(/[^A-Z0-9_]/g, '')
  const label = newTypeLabel.value.trim()
  if (!name || !label) return
  if (form.documentTypes!.some(dt => dt.name === name)) return
  form.documentTypes!.push({ name, label, chunkSize: 512, chunkOverlap: 50 })
  newTypeName.value = ''
  newTypeLabel.value = ''
}

function removeDocType(idx: number) {
  form.documentTypes!.splice(idx, 1)
}

watch(() => settings.value, (s) => {
  if (s) {
    Object.assign(form.llm, s.llm)
    Object.assign(form.embedding, s.embedding)
    Object.assign(form.vectorStore, s.vectorStore)
    Object.assign(form.rag, s.rag)
    Object.assign(form.a2a, s.a2a)
    if (s.webSearch) Object.assign(form.webSearch!, s.webSearch)
    if (s.documentTypes && s.documentTypes.length > 0) {
      form.documentTypes = s.documentTypes.map(dt => ({ ...dt }))
    }
    if (s.prompts && Object.keys(s.prompts).length > 0) {
      form.prompts = Object.fromEntries(
        Object.entries(s.prompts).map(([k, v]) => [k, { ...v }])
      )
      if (!promptDefaults.value) {
        promptDefaults.value = Object.fromEntries(
          Object.entries(s.prompts).map(([k, v]) => [k, { ...v }])
        )
      }
    }
    llmTemp.value = s.llm.temperature ?? 0.7
    llmTokens.value = s.llm.maxTokens ?? 2048
  }
}, { immediate: true })

watch(llmTemp, (v) => { form.llm.temperature = v })
watch(llmTokens, (v) => { form.llm.maxTokens = v })

async function handleSave() {
  await saveSettings(form)
}

async function handleReindex() {
  await saveSettings(form)
  await reindex()
}

onMounted(() => {
  fetchSettings()
})
</script>

<style scoped>
.settings-page {
  padding: 24px 32px 40px;
}
.grid-2col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}
.settings-card {
  background: #FFFFFF;
  border: 1px solid #E8DDD0;
  border-radius: 12px;
  overflow: hidden;
}
.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 18px;
  border-bottom: 1px solid #F5F0EB;
  background: #FEFCFA;
}
.card-title {
  font-size: 13px;
  font-weight: 600;
  color: #3D3028;
}
.settings-card :deep(.n-form) {
  padding: 14px 18px;
}
.settings-card :deep(.n-form-item) {
  margin-bottom: 14px;
}
.settings-card :deep(.n-form-item:last-child) {
  margin-bottom: 0;
}
.settings-card :deep(.n-form-item-label) {
  color: #6B5E52;
  font-size: 12px;
  font-weight: 500;
}
.settings-card :deep(.n-input),
.settings-card :deep(.n-select),
.settings-card :deep(.n-input-number) {
  font-size: 13px;
}
.settings-card :deep(.n-input-number) {
  width: 100%;
}
.settings-card :deep(.n-slider) {
  margin-top: 4px;
}
.field-hint {
  font-size: 11px;
  color: #B8A898;
  line-height: 1.5;
  display: inline-block;
  margin-top: 2px;
}
.info-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #FEF3E8;
  border: 1px solid #F5DFC8;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 12px;
  color: #8B6914;
  margin-top: 4px;
  line-height: 1.5;
}

/* Actions */
.actions-bar {
  display: flex;
  justify-content: center;
  gap: 12px;
  padding: 4px 0 8px;
}
.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0 28px;
  height: 40px;
  border-radius: 10px;
  border: none;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  font-family: inherit;
  transition: all 0.2s;
}
.action-btn.primary {
  background: linear-gradient(135deg, #E8913A, #D97B2B);
  color: white;
}
.action-btn.primary:hover:not(:disabled) {
  box-shadow: 0 4px 12px rgba(217, 123, 43, 0.35);
  transform: translateY(-1px);
}
.action-btn.warning {
  background: #FFFFFF;
  color: #D97B2B;
  border: 1px solid #E8DDD0;
}
.action-btn.warning:hover:not(:disabled) {
  background: #FEF3E8;
  border-color: #F5DFC8;
}
.action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* Document Type Grid */
.doc-type-grid {
  padding: 14px 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.doc-type-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.doc-type-name-group {
  display: flex;
  gap: 6px;
  min-width: 200px;
}
.doc-type-name-input {
  width: 100px;
  padding: 4px 8px;
  border: 1px solid #E8DDD0;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #3D3028;
  background: #FEFCFA;
  font-family: monospace;
  text-transform: uppercase;
}
.doc-type-name-input:focus { outline: none; border-color: #D97B2B; }
.doc-type-label-input {
  width: 90px;
  padding: 4px 8px;
  border: 1px solid #E8DDD0;
  border-radius: 6px;
  font-size: 12px;
  color: #6B5E52;
  background: #FFFFFF;
}
.doc-type-label-input:focus { outline: none; border-color: #D97B2B; }
.doc-type-fields {
  display: flex;
  gap: 16px;
  flex: 1;
}
.field-pair {
  display: flex;
  align-items: center;
  gap: 8px;
}
.field-pair label {
  font-size: 12px;
  color: #8B7E74;
  white-space: nowrap;
}
.field-pair :deep(.n-input-number) {
  width: 120px;
}
.doc-type-delete {
  border: none;
  background: none;
  cursor: pointer;
  color: #C8B8A8;
  padding: 4px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  transition: all 0.2s;
  flex-shrink: 0;
}
.doc-type-delete:hover { color: #E85D5D; background: #FFEBEE; }

/* Add Type Bar */
.doc-type-add-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 18px;
  border-top: 1px solid #F5F0EB;
  background: #FEFCFA;
}
.new-name { width: 120px; }
.new-label { width: 110px; }
.add-type-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 14px;
  border-radius: 8px;
  border: 1px solid #E8DDD0;
  background: #FAF7F2;
  color: #D97B2B;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}
.add-type-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, #FEF3E8, #FFF5ED);
  border-color: #D97B2B;
}
.add-type-btn:disabled { opacity: 0.4; cursor: not-allowed; }

/* Toast */
.toast-msg {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 500;
  max-width: 600px;
  margin: 0 auto;
  animation: fadeIn 0.3s ease;
}
.toast-msg.success {
  background: #E8F5E9;
  color: #2E7D32;
  border: 1px solid #C8E6C9;
}
.toast-msg.error {
  background: #FFEBEE;
  color: #C62828;
  border: 1px solid #FFCDD2;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(-4px); }
  to { opacity: 1; transform: translateY(0); }
}

/* Web Search styles */
.ws-status {
  font-size: 11px; font-weight: 600; padding: 2px 8px;
  border-radius: 10px; margin-left: auto; letter-spacing: 0.3px;
}
.ws-on { background: #dcfce7; color: #16a34a; }
.ws-off { background: #f3f4f6; color: #9ca3af; }

.ws-provider-grid {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin-bottom: 14px;
}
.ws-provider-card {
  display: flex; align-items: center; gap: 8px; padding: 10px 10px;
  border: 2px solid #e8e8e8; border-radius: 8px; cursor: pointer;
  transition: all 0.2s; background: #fff;
}
.ws-provider-card:hover { border-color: #c0c0c0; background: #fafafa; }
.ws-provider-card.active { border-color: #2563eb; background: #eff6ff; }
.ws-provider-card input[type="radio"] { display: none; }
.ws-provider-icon { font-size: 20px; line-height: 1; }
.ws-provider-info { min-width: 0; }
.ws-provider-name { font-size: 13px; font-weight: 600; color: #333; }
.ws-provider-desc { font-size: 10px; color: #888; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

.ws-config-section {
  background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px;
  padding: 14px; margin-bottom: 10px;
}
.ws-config-row { display: flex; gap: 12px; flex-wrap: wrap; }
.ws-field { flex: 1; min-width: 180px; }
.ws-field-sm { flex: 0 0 120px; min-width: 100px; }
.ws-label { display: block; font-size: 12px; font-weight: 500; color: #555; margin-bottom: 4px; }
.ws-input-wrap {
  display: flex; align-items: center; gap: 6px;
  background: #fff; border: 1px solid #d1d5db; border-radius: 6px;
  padding: 0 8px; transition: border-color 0.2s;
}
.ws-input-wrap:focus-within { border-color: #2563eb; box-shadow: 0 0 0 2px rgba(37,99,235,0.1); }
.ws-input-icon { flex-shrink: 0; }
.ws-input {
  flex: 1; border: none; outline: none; padding: 7px 0;
  font-size: 13px; background: transparent; color: #333;
  font-family: 'SF Mono', 'Cascadia Code', monospace;
}
.ws-input::placeholder { color: #b0b0b0; }
.ws-toggle-vis {
  background: none; border: none; cursor: pointer; padding: 2px;
  display: flex; align-items: center; color: #888; flex-shrink: 0;
}
.ws-toggle-vis:hover { color: #333; }

.slide-fade-enter-active { transition: all 0.25s ease-out; }
.slide-fade-leave-active { transition: all 0.15s ease-in; }
.slide-fade-enter-from { opacity: 0; transform: translateY(-8px); }
.slide-fade-leave-to { opacity: 0; transform: translateY(-4px); }

/* Prompt Management */
.prompt-count { font-size: 11px; color: #B8A898; margin-left: auto; }
.chevron { transition: transform 0.2s; }
.chevron.expanded { transform: rotate(180deg); }
.prompt-section { padding: 0 0 8px; }
.prompt-category { border-bottom: 1px solid #F5F0EB; }
.prompt-category:last-child { border-bottom: none; }
.prompt-cat-header { display: flex; align-items: center; gap: 8px; padding: 10px 18px; cursor: pointer; }
.prompt-cat-header:hover { background: #FEFCFA; }
.prompt-cat-label { font-size: 13px; font-weight: 600; color: #3D3028; }
.prompt-cat-count { font-size: 11px; color: #B8A898; background: #F0E8DD; padding: 1px 6px; border-radius: 8px; }
.prompt-items { padding: 0 18px 12px; display: flex; flex-direction: column; gap: 12px; }
.prompt-item { background: #FEFCFA; border: 1px solid #F0E8DD; border-radius: 8px; padding: 10px 12px; }
.prompt-item-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; }
.prompt-item-desc { font-size: 12px; font-weight: 600; color: #6B5E52; }
.prompt-reset-btn { display: inline-flex; align-items: center; gap: 3px; background: none; border: 1px solid #E8DDD0; border-radius: 5px; font-size: 11px; color: #B8A898; cursor: pointer; padding: 2px 8px; }
.prompt-reset-btn:hover { color: #D97B2B; border-color: #D97B2B; }
.prompt-textarea { width: 100%; border: 1px solid #E8DDD0; border-radius: 6px; padding: 8px 10px; font-size: 12px; font-family: 'SF Mono', 'Cascadia Code', 'Consolas', monospace; line-height: 1.5; resize: vertical; min-height: 80px; outline: none; background: #fff; color: #3D3028; }
.prompt-textarea:focus { border-color: #D97B2B; }
.prompt-var-hint { margin-top: 4px; font-size: 11px; color: #B8A898; display: flex; align-items: center; gap: 4px; flex-wrap: wrap; }
.prompt-var-hint code { background: #F0E8DD; padding: 1px 5px; border-radius: 3px; font-size: 10px; color: #8B7E74; font-family: monospace; }
.prompt-section .info-tip { margin: 8px 18px 10px; }
.prompt-section .info-tip code { background: #F0E8DD; padding: 1px 5px; border-radius: 3px; font-size: 11px; color: #8B6914; font-family: monospace; }
</style>
