<template>
  <div class="settings-page">
    <n-spin :show="loading">
      <!-- Top row: LLM Provider Management (full width) -->
      <div class="settings-card llm-provider-card">
        <div class="card-header">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#E8913A" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2a3 3 0 0 0-3 3v1H4a2 2 0 0 0-2 2v4a2 2 0 0 0 2 2h1v5a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-5h1a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-5V5a3 3 0 0 0-3-3z"/></svg>
          <span class="card-title">LLM 大语言模型</span>
          <button class="llm-add-btn" @click="startCreateProvider">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            添加
          </button>
        </div>

        <!-- Provider grid (cards) -->
        <div class="llm-provider-grid">
          <div v-for="p in providers" :key="p.id"
               :class="['llm-provider-card-item', { active: selectedProviderId === p.id, current: p.isDefault }]"
               @click="selectProvider(p.id)">
            <div class="llm-card-top">
              <span :class="['llm-provider-icon', p.provider]">{{ providerIcon(p.provider) }}</span>
              <div class="llm-card-title-row">
                <span class="llm-card-name">{{ p.name }}</span>
                <span v-if="p.isDefault" class="llm-current-tag">使用中</span>
              </div>
            </div>
            <div class="llm-card-detail">
              <span class="llm-card-label">{{ p.provider }}</span>
              <span class="llm-card-divider">&middot;</span>
              <span class="llm-card-model">{{ p.modelName }}</span>
            </div>
          </div>
          <div class="llm-provider-card-item llm-add-card" @click="startCreateProvider">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#C8B8A8" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            <span>添加新配置</span>
          </div>
        </div>

        <!-- Provider edit form (collapsible) -->
        <transition name="llm-slide">
          <div v-if="selectedProviderId || isCreatingProvider" class="llm-edit-section">
            <div class="llm-edit-header">
              <span class="llm-edit-title">{{ isCreatingProvider ? '新建配置' : '编辑配置' }}</span>
              <button class="llm-edit-close" @click="cancelProviderForm">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
              </button>
            </div>
            <div class="llm-edit-form-grid">
              <n-form label-placement="left" label-width="85">
                <n-form-item label="名称">
                  <n-input v-model:value="providerForm.name" placeholder="例如：Ollama 本地" />
                </n-form-item>
                <n-form-item label="Provider">
                  <n-select :value="providerForm.provider" :options="providerOptions"
                            @update:value="(v: string) => onProviderFormChange(v)" />
                </n-form-item>
                <n-form-item label="Base URL">
                  <n-input v-model:value="providerForm.baseUrl" placeholder="http://localhost:11434/v1" />
                </n-form-item>
                <n-form-item label="API Key">
                  <n-input v-model:value="providerForm.apiKey" type="password" show-password-on="click" placeholder="API Key" />
                </n-form-item>
                <n-form-item label="Model">
                  <n-input v-model:value="providerForm.modelName" placeholder="qwen2.5" />
                </n-form-item>
                <n-form-item label="Temperature">
                  <n-slider v-model:value="providerForm.temperature" :min="0" :max="2" :step="0.1" />
                </n-form-item>
                <n-form-item label="Max Tokens">
                  <n-input-number v-model:value="providerForm.maxTokens" :min="128" :max="32768" :step="256" />
                </n-form-item>
                <n-form-item label="流式输出">
                  <n-switch v-model:value="providerForm.streaming" />
                </n-form-item>
              </n-form>
            </div>
            <div class="llm-edit-actions">
              <template v-if="isCreatingProvider">
                <button class="llm-action-btn primary" @click="handleCreateProvider">创建</button>
                <button class="llm-action-btn" @click="cancelProviderForm">取消</button>
              </template>
              <template v-else>
                <button class="llm-action-btn primary" @click="handleSaveProvider">保存</button>
                <button v-if="selectedProvider && !selectedProvider.isDefault"
                        class="llm-action-btn activate" @click="handleActivateProvider">
                  设为当前
                </button>
                <button v-if="selectedProvider && !selectedProvider.isDefault"
                        class="llm-action-btn danger" @click="handleDeleteProvider">
                  删除
                </button>
              </template>
            </div>
          </div>
        </transition>
      </div>

      <!-- Embedding Config (full width below LLM) -->
      <div class="settings-card" style="margin-bottom: 16px">
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


      <!-- Security: Admin Password -->
      <div class="settings-card" style="margin-bottom: 16px">
        <div class="card-header">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#E8913A" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
          <span class="card-title">安全设置</span>
        </div>
        <n-form label-placement="left" label-width="100">
          <n-form-item label="管理密码">
            <n-input v-model:value="form.blog!.adminPassword" type="password" show-password-on="click" placeholder="留空则免登录" />
            <template #feedback>
              <span class="field-hint">留空 = 无需密码直接进入管理后台。设置密码后需登录才能访问管理功能。修改后需保存并重新登录。</span>
            </template>
          </n-form-item>
        </n-form>
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
import { ref, reactive, computed, onMounted, watch } from 'vue'
import {
  NForm, NFormItem, NInput, NInputNumber, NSelect, NSlider,
  NSpin, NSwitch,
} from 'naive-ui'
import { useSettings } from '../composables/useSettings'
import { PROVIDER_PRESETS, EMBEDDING_PRESETS } from '../types/chat'
import type { AppSettings, DocumentTypeConfig, PromptEntry } from '../types/chat'
import { useAdmin } from '../composables/useAdmin'
import { useLlmProviders } from '../composables/useLlmProviders'

const { settings, loading, saving, message, fetchSettings, saveSettings, reindex } = useSettings()
const { logout } = useAdmin()
const { providers, activeProvider, loadProviders, createProvider, updateProvider, deleteProvider, activateProvider } = useLlmProviders()

const form = reactive<AppSettings>({
  llm: { provider: 'ollama', baseUrl: 'http://localhost:11434/v1', apiKey: 'ollama', modelName: 'qwen2.5', temperature: 0.7, maxTokens: 2048, streaming: true },
  embedding: { provider: 'ollama', baseUrl: 'http://localhost:11434/v1', apiKey: 'ollama', modelName: 'nomic-embed-text' },
  vectorStore: { type: 'memory', chromaBaseUrl: 'http://localhost:8000', collectionName: 'rag_knowledge_base', milvusHost: 'localhost', milvusPort: 19530, embeddingDimension: 768 },
  rag: { chunkSize: 300, chunkOverlap: 30, vectorTopK: 5, keywordTopK: 10, rrfK: 60, minScore: 0.3 },
  a2a: { enabled: true, agentName: '文枢', agentDescription: '' },
  blog: { adminPassword: '' },
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

// Provider management state
const selectedProviderId = ref<string | null>(null)
const isCreatingProvider = ref(false)
const providerForm = reactive({
  name: '', provider: 'ollama', baseUrl: 'http://localhost:11434/v1',
  apiKey: 'ollama', modelName: 'qwen2.5',
  temperature: 0.7, maxTokens: 2048, streaming: true,
})

const selectedProvider = computed(() =>
  selectedProviderId.value ? providers.value.find(p => p.id === selectedProviderId.value) : null
)

function selectProvider(id: string) {
  isCreatingProvider.value = false
  selectedProviderId.value = id
  const p = providers.value.find(p => p.id === id)
  if (p) {
    providerForm.name = p.name
    providerForm.provider = p.provider
    providerForm.baseUrl = p.baseUrl
    providerForm.apiKey = p.apiKey
    providerForm.modelName = p.modelName
    providerForm.temperature = p.temperature ?? 0.7
    providerForm.maxTokens = p.maxTokens ?? 2048
    providerForm.streaming = p.streaming
  }
}

function startCreateProvider() {
  isCreatingProvider.value = true
  selectedProviderId.value = null
  providerForm.name = ''
  providerForm.provider = 'ollama'
  providerForm.baseUrl = 'http://localhost:11434/v1'
  providerForm.apiKey = 'ollama'
  providerForm.modelName = 'qwen2.5'
  providerForm.temperature = 0.7
  providerForm.maxTokens = 2048
  providerForm.streaming = true
}

function cancelProviderForm() {
  isCreatingProvider.value = false
  selectedProviderId.value = null
}

function onProviderFormChange(provider: string) {
  const preset = PROVIDER_PRESETS[provider]
  if (preset) {
    providerForm.provider = preset.provider!
    providerForm.baseUrl = preset.baseUrl!
    providerForm.modelName = preset.modelName!
    if (preset.apiKey !== undefined) providerForm.apiKey = preset.apiKey
  }
}

async function handleCreateProvider() {
  const result = await createProvider({
    name: providerForm.name || providerForm.provider,
    provider: providerForm.provider,
    baseUrl: providerForm.baseUrl,
    apiKey: providerForm.apiKey,
    modelName: providerForm.modelName,
    temperature: providerForm.temperature,
    maxTokens: providerForm.maxTokens,
    streaming: providerForm.streaming,
  })
  if (result) {
    isCreatingProvider.value = false
    selectProvider(result.id)
  }
}

async function handleSaveProvider() {
  if (!selectedProviderId.value) return
  await updateProvider(selectedProviderId.value, {
    name: providerForm.name,
    provider: providerForm.provider,
    baseUrl: providerForm.baseUrl,
    apiKey: providerForm.apiKey,
    modelName: providerForm.modelName,
    temperature: providerForm.temperature,
    maxTokens: providerForm.maxTokens,
    streaming: providerForm.streaming,
  })
  await fetchSettings()
}

async function handleActivateProvider() {
  if (!selectedProviderId.value) return
  const ok = await activateProvider(selectedProviderId.value)
  if (ok) {
    await fetchSettings()
  }
}

async function handleDeleteProvider() {
  if (!selectedProviderId.value) return
  if (!confirm('确定删除此 LLM 配置？')) return
  const ok = await deleteProvider(selectedProviderId.value)
  if (ok) {
    selectedProviderId.value = null
  }
}

function providerIcon(provider: string): string {
  switch (provider) {
    case 'ollama': return '🦙'
    case 'openai': return '✦'
    case 'minimax': return '◈'
    default: return '⚙'
  }
}

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
    if (s.blog) Object.assign(form.blog!, s.blog)
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
  const payload = { ...form }
  await saveSettings(payload)
}

async function handleReindex() {
  await saveSettings(form)
  await reindex()
}

onMounted(() => {
  fetchSettings()
  loadProviders()
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

/* LLM Provider Management */
.llm-provider-card { margin-bottom: 16px; }
.llm-add-btn {
  display: inline-flex; align-items: center; gap: 4px;
  margin-left: auto; padding: 5px 14px; border-radius: 8px;
  border: 1px dashed #D97B2B; background: #FEF3E8;
  color: #D97B2B; font-size: 12px; font-weight: 600;
  cursor: pointer; transition: all 0.2s;
}
.llm-add-btn:hover { background: #FDE8D4; border-style: solid; }

/* Provider card grid */
.llm-provider-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px; padding: 16px 18px;
}
.llm-provider-card-item {
  position: relative; padding: 14px; border-radius: 10px;
  border: 1.5px solid #E8DDD0; background: #FAFAF6;
  cursor: pointer; transition: all 0.2s;
}
.llm-provider-card-item:hover { border-color: #D4C0A8; box-shadow: 0 2px 8px rgba(0,0,0,0.04); }
.llm-provider-card-item.active { border-color: #D97B2B; background: #FEF3E8; box-shadow: 0 0 0 1px #D97B2B; }
.llm-provider-card-item.current { border-color: #4CAF50; }
.llm-provider-card-item.current::after {
  content: ''; position: absolute; top: 8px; right: 8px;
  width: 8px; height: 8px; border-radius: 50%;
  background: #4CAF50;
}
.llm-add-card {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 6px; border-style: dashed; color: #C8B8A8; font-size: 12px;
}
.llm-add-card:hover { border-color: #D97B2B; color: #D97B2B; }

.llm-card-top { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.llm-provider-icon {
  width: 36px; height: 36px; border-radius: 8px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  font-size: 18px; background: #F0E8DD;
}
.llm-provider-icon.ollama { background: #FEF3E8; }
.llm-provider-icon.openai { background: #E3F2FD; }
.llm-provider-icon.minimax { background: #FFF3E0; }
.llm-card-title-row { flex: 1; min-width: 0; display: flex; align-items: center; gap: 6px; }
.llm-card-name {
  font-size: 13px; font-weight: 600; color: #3D3028;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.llm-current-tag {
  font-size: 10px; font-weight: 700; color: #4CAF50;
  background: #E8F5E9; padding: 2px 6px; border-radius: 4px;
  flex-shrink: 0; letter-spacing: 0.3px;
}
.llm-card-detail {
  font-size: 12px; color: #A89888; display: flex; align-items: center; gap: 4px;
}
.llm-card-label {
  padding: 1px 6px; border-radius: 4px; background: #F0E8DD; color: #8B7E74;
  font-size: 10px; font-weight: 600; text-transform: uppercase;
}
.llm-card-divider { color: #D4C8BA; }
.llm-card-model { color: #8B7E74; }

/* Edit section (slide down) */
.llm-edit-section {
  border-top: 1px solid #F0E8DD; background: #FEFCFA;
}
.llm-edit-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 18px; border-bottom: 1px solid #F0E8DD;
}
.llm-edit-title { font-size: 13px; font-weight: 600; color: #3D3028; }
.llm-edit-close {
  width: 28px; height: 28px; border-radius: 6px;
  border: none; background: none; cursor: pointer;
  color: #B8A898; display: flex; align-items: center; justify-content: center;
  transition: all 0.15s;
}
.llm-edit-close:hover { background: #FFEBEE; color: #E85D5D; }
.llm-edit-form-grid { padding: 0 18px; }
.llm-edit-actions {
  display: flex; gap: 8px; padding: 12px 18px;
  border-top: 1px solid #F0E8DD;
}
.llm-action-btn {
  padding: 6px 18px; border-radius: 6px; border: 1px solid #E8DDD0;
  background: #FAF7F2; color: #8B7E74; font-size: 12px; font-weight: 500;
  cursor: pointer; transition: all 0.2s;
}
.llm-action-btn.primary {
  background: #D97B2B; color: #fff; border-color: #D97B2B;
}
.llm-action-btn.primary:hover { background: #C86A20; }
.llm-action-btn.activate { border-color: #4CAF50; color: #4CAF50; }
.llm-action-btn.activate:hover { background: #E8F5E9; }
.llm-action-btn.danger { border-color: #E85D5D; color: #E85D5D; }
.llm-action-btn.danger:hover { background: #FFEBEE; }

/* Slide transition */
.llm-slide-enter-active { transition: all 0.25s ease-out; }
.llm-slide-leave-active { transition: all 0.15s ease-in; }
.llm-slide-enter-from, .llm-slide-leave-to {
  opacity: 0; max-height: 0; overflow: hidden;
}
.llm-slide-enter-to, .llm-slide-leave-from {
  opacity: 1; max-height: 600px;
}

</style>
