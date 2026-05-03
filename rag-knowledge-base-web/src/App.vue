<template>
  <n-config-provider :theme-overrides="warmTheme">
    <n-message-provider>
      <div class="app-shell">
        <!-- Left Sidebar Nav -->
        <aside class="sidebar">
          <div class="sidebar-logo">
            <img src="/WenShu.png" alt="WenShu" />
          </div>
          <nav class="sidebar-nav">
            <button :class="['nav-btn', { active: currentPage === 'chat' }]" @click="switchPage('chat')" title="藏书阁">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
            </button>
            <button :class="['nav-btn', { active: currentPage === 'blog' || currentPage === 'blog-detail' }]" @click="switchPage('blog')" title="博客">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"/><path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"/></svg>
            </button>
            <button :class="['nav-btn', { active: currentPage === 'flashcard' || currentPage === 'flashcard-detail' || currentPage === 'flashcard-study' }]" @click="switchPage('flashcard')" title="闪卡">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="4" width="20" height="16" rx="2"/><line x1="2" y1="10" x2="22" y2="10"/><line x1="12" y1="4" x2="12" y2="20"/></svg>
            </button>
            <button :class="['nav-btn', { active: currentPage === 'a2a' }]" @click="switchPage('a2a')" title="A2A 节点">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="2"/><path d="M16.24 7.76a6 6 0 0 1 0 8.49m-8.48-.01a6 6 0 0 1 0-8.49m11.31-2.82a10 10 0 0 1 0 14.14m-14.14 0a10 10 0 0 1 0-14.14"/></svg>
            </button>
          </nav>
          <div class="sidebar-divider"></div>
          <button :class="['nav-btn', { active: currentPage.startsWith('admin') }]" @click="handleAdminClick" title="管理后台">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
          </button>
          <div class="sidebar-status">
            <span :class="['status-dot', healthStatus === 'running' ? 'ok' : 'err']"></span>
          </div>
        </aside>

        <!-- Main Area -->
        <main class="main-area">
          <!-- Top bar -->
          <header class="topbar">
            <h1 class="topbar-title">{{ pageTitle }}</h1>
            <div class="topbar-actions">
              <button v-if="currentPage === 'chat'" class="new-chat-btn" @click="createConversation" title="新对话">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
                新对话
              </button>
              <span v-if="stats && !currentPage.startsWith('admin')" class="topbar-info">
                {{ stats.llmModel }} &middot; {{ stats.segmentCount }} segments
              </span>
            </div>
          </header>

          <!-- Content -->
          <div class="content-wrapper">
            <!-- Chat Page -->
            <template v-if="currentPage === 'chat'">
              <div class="chat-area">
                <ChatPanel />
              </div>
              <div :class="['side-panel', { collapsed: sidePanelCollapsed }]">
                <button class="side-panel-toggle" @click="sidePanelCollapsed = !sidePanelCollapsed" :title="sidePanelCollapsed ? '展开面板' : '收起面板'">
                  {{ sidePanelCollapsed ? '<' : '>' }}
                </button>
                <div class="side-panel-content" v-show="!sidePanelCollapsed">
                  <ConversationList
                    :conversations="conversations"
                    :current-id="currentConversationId"
                    @create="createConversation"
                    @switch="switchConversation"
                    @delete="deleteConversation"
                  />
                  <KnowledgeStats :stats="stats" />
                  <AgentCard :agent-card="agentCard" />
                </div>
              </div>
            </template>

            <!-- Blog Page -->
            <template v-if="currentPage === 'blog'">
              <BlogList @open-article="openArticle" />
            </template>

            <!-- Blog Detail -->
            <template v-if="currentPage === 'blog-detail'">
              <BlogDetail :article="currentArticle" @back="switchPage('blog')" />
            </template>

            <!-- Flashcard Pages -->
            <template v-if="currentPage === 'flashcard'">
              <FlashcardPage @open-deck="(id: string) => { currentDeckId = id; currentPage = 'flashcard-detail' }"
                             @study="(id: string) => { currentStudyDeckId = id; currentPage = 'flashcard-study' }" />
            </template>
            <template v-if="currentPage === 'flashcard-detail'">
              <DeckDetail :deck-id="currentDeckId" @back="currentPage = 'flashcard'"
                          @study="(id: string) => { currentStudyDeckId = id; currentPage = 'flashcard-study' }" />
            </template>
            <template v-if="currentPage === 'flashcard-study'">
              <StudyMode :deck-id="currentStudyDeckId" @back="currentPage = 'flashcard-detail'" />
            </template>

            <!-- A2A Page -->
            <template v-if="currentPage === 'a2a'">
              <div class="a2a-page">
                <div class="a2a-container">
                  <div class="a2a-hero" v-if="agentCard">
                    <div class="hero-accent"></div>
                    <div class="hero-body">
                      <div class="hero-top">
                        <div class="hero-status"><span class="hero-dot"></span><span>在线</span></div>
                        <span class="hero-version">v{{ agentCard.version }}</span>
                      </div>
                      <h2 class="hero-name">{{ agentCard.name }}</h2>
                      <p class="hero-desc" v-if="agentCard.description">{{ agentCard.description }}</p>
                      <div class="hero-endpoint">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/></svg>
                        <code>{{ agentCard.url }}</code>
                      </div>
                      <div class="hero-tags">
                        <span v-for="skill in agentCard.skills" :key="skill.id" class="hero-skill">{{ skill.name }}</span>
                        <template v-if="agentCard.capabilities">
                          <span :class="['hero-cap', agentCard.capabilities.streaming ? 'on' : 'off']">{{ agentCard.capabilities.streaming ? '流式输出' : '流式输出(关)' }}</span>
                          <span :class="['hero-cap', agentCard.capabilities.pushNotifications ? 'on' : 'off']">{{ agentCard.capabilities.pushNotifications ? '推送通知' : '推送通知(关)' }}</span>
                        </template>
                      </div>
                    </div>
                  </div>
                  <div class="a2a-hero a2a-hero-offline" v-else>
                    <div class="hero-body">
                      <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#D4C8BA" stroke-width="1.5"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
                      <h2 class="hero-name" style="color: #B8A898">Agent 暂不可用</h2>
                    </div>
                  </div>
                  <KnowledgeStats :stats="stats" />
                  <TaskList :tasks="tasks" />
                </div>
              </div>
            </template>

            <!-- Admin Login -->
            <template v-if="currentPage === 'admin-login'">
              <div class="admin-login">
                <div class="login-card">
                  <h2 class="login-title">管理后台</h2>
                  <p class="login-desc">请输入管理员密码</p>
                  <input type="password" v-model="adminPassword" class="login-input"
                         placeholder="密码" @keydown.enter="handleAdminLogin" />
                  <p v-if="adminLoginError" class="login-error">密码错误</p>
                  <button class="login-btn" @click="handleAdminLogin">登录</button>
                </div>
              </div>
            </template>

            <!-- Admin Panel (tab-based) -->
            <template v-if="isAdminPage">
              <div class="admin-panel">
                <div class="admin-tabs">
                  <button :class="['admin-tab-btn', { active: adminTab === 'posts' }]" @click="adminTab = 'posts'">
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
                    文章管理
                  </button>
                  <button :class="['admin-tab-btn', { active: adminTab === 'knowledge' }]" @click="adminTab = 'knowledge'">
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
                    知识库
                  </button>
                  <button :class="['admin-tab-btn', { active: adminTab === 'agents' }]" @click="adminTab = 'agents'">
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
                    智能体
                  </button>
                  <button :class="['admin-tab-btn', { active: adminTab === 'settings' }]" @click="adminTab = 'settings'">
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>
                    系统设置
                  </button>
                  <button :class="['admin-tab-btn', { active: adminTab === 'categories' }]" @click="adminTab = 'categories'">
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>
                    分类管理
                  </button>
                  <button :class="['admin-tab-btn', { active: adminTab === 'media' }]" @click="adminTab = 'media'">
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"/><circle cx="8.5" cy="8.5" r="1.5"/><polyline points="21 15 16 10 5 21"/></svg>
                    媒体库
                  </button>
                  <button :class="['admin-tab-btn', { active: adminTab === 'memories' }]" @click="adminTab = 'memories'">
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2a8 8 0 0 0-8 8c0 6 8 12 8 12s8-6 8-12a8 8 0 0 0-8-8z"/><circle cx="12" cy="10" r="3" fill="currentColor" opacity="0.3"/></svg>
                    记忆管理
                  </button>
                  <button :class="['admin-tab-btn', { active: adminTab === 'eval' }]" @click="adminTab = 'eval'">
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 11l3 3L22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/></svg>
                    质量评估
                  </button>
                  <button :class="['admin-tab-btn', { active: adminTab === 'llm' }]" @click="adminTab = 'llm'">
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>
                    LLM 监控
                  </button>
                  <button :class="['admin-tab-btn', { active: adminTab === 'prompts' }]" @click="adminTab = 'prompts'">
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/></svg>
                    Prompt 管理
                  </button>
                  <div class="admin-tabs-spacer"></div>
                  <button class="admin-logout-btn" @click="handleLogout">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>
                    退出
                  </button>
                </div>

                <div class="admin-content">
                  <template v-if="adminTab === 'posts' && currentPage === 'admin'">
                    <PostList @create="openEditor()" @edit="(a: Article) => openEditor(a)" />
                  </template>
                  <template v-if="adminTab === 'posts' && currentPage === 'admin-editor'">
                    <PostEditor :edit-article="editingArticle" @back="currentPage = 'admin'" @saved="onArticleSaved" />
                  </template>
                  <template v-if="adminTab === 'knowledge'">
                    <KnowledgeBase />
                  </template>
                  <template v-if="adminTab === 'agents'">
                    <AgentManager />
                  </template>
                  <template v-if="adminTab === 'settings'">
                    <SettingsPage />
                  </template>
                  <template v-if="adminTab === 'categories'">
                    <CategoryManager />
                  </template>
                  <template v-if="adminTab === 'media'">
                    <MediaManager />
                  </template>
                  <template v-if="adminTab === 'memories'">
                    <MemoryManager />
                  </template>
                  <template v-if="adminTab === 'eval'">
                    <EvalDashboard />
                  </template>
                  <template v-if="adminTab === 'llm'">
                    <LlmMonitor />
                  </template>
                  <template v-if="adminTab === 'prompts'">
                    <PromptManagePage />
                  </template>
                </div>
              </div>
            </template>
          </div>
        </main>
      </div>
    </n-message-provider>
  </n-config-provider>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { NConfigProvider, NMessageProvider } from 'naive-ui'
import type { GlobalThemeOverrides } from 'naive-ui'
import ChatPanel from './components/ChatPanel.vue'
import SettingsPage from './components/SettingsPage.vue'
import KnowledgeBase from './components/KnowledgeBase.vue'
import KnowledgeStats from './components/KnowledgeStats.vue'
import AgentCard from './components/AgentCard.vue'
import AgentManager from './components/AgentManager.vue'
import TaskList from './components/TaskList.vue'
import ConversationList from './components/ConversationList.vue'
import BlogList from './components/blog/BlogList.vue'
import BlogDetail from './components/blog/BlogDetail.vue'
import PostEditor from './components/admin/PostEditor.vue'
import PostList from './components/admin/PostList.vue'
import CategoryManager from './components/admin/CategoryManager.vue'
import MediaManager from './components/admin/MediaManager.vue'
import MemoryManager from './components/admin/MemoryManager.vue'
import EvalDashboard from './components/admin/EvalDashboard.vue'
import LlmMonitor from './components/admin/LlmMonitor.vue'
import PromptManagePage from './components/PromptManagePage.vue'
import FlashcardPage from './components/flashcard/FlashcardPage.vue'
import DeckDetail from './components/flashcard/DeckDetail.vue'
import StudyMode from './components/flashcard/StudyMode.vue'
import { useA2aClient } from './composables/useA2aClient'
import { useSettings } from './composables/useSettings'
import { useChat } from './composables/useChat'
import { useAgents } from './composables/useAgents'
import { useBlog } from './composables/useBlog'
import { useAdmin } from './composables/useAdmin'
import type { KnowledgeStats as Stats } from './types/chat'
import type { Article } from './types/blog'

const warmTheme: GlobalThemeOverrides = {
  common: {
    primaryColor: '#D97B2B',
    primaryColorHover: '#C06A1E',
    primaryColorPressed: '#B05D15',
    primaryColorSuppl: '#E8913A',
    borderRadius: '8px',
    fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif",
  },
  Button: { borderRadiusMedium: '8px' },
  Card: { borderRadius: '10px' },
  Input: { borderRadius: '8px' },
}

const currentPage = ref('chat')
const adminTab = ref('posts')
const sidePanelCollapsed = ref(false)
const healthStatus = ref('')

const { agentCard, tasks, fetchAgentCard, fetchTasks } = useA2aClient()
const { fetchStats } = useSettings()
const { conversations, currentConversationId, loadConversations, createConversation, switchConversation, deleteConversation } = useChat()
const { loadAgents } = useAgents()
const { getPost } = useBlog()
const { isLoggedIn, login, loginNoPassword, checkAuthStatus, passwordEnabled, logout } = useAdmin()
const stats = ref<Stats | null>(null)

const currentArticle = ref<Article | null>(null)
const editingArticle = ref<Article | null>(null)
const currentDeckId = ref('')
const currentStudyDeckId = ref('')
const adminPassword = ref('')
const adminLoginError = ref(false)

const isAdminPage = computed(() => ['admin', 'admin-editor', 'admin-posts'].includes(currentPage.value))

const pageTitle = computed(() => {
  if (currentPage.value === 'admin-editor' && adminTab.value === 'posts') return editingArticle.value ? '编辑文章' : '新建文章'
  switch (currentPage.value) {
    case 'chat': return '文枢 · 藏书阁'
    case 'blog': return '文枢 · 博客'
    case 'blog-detail': return currentArticle.value?.title || '文枢 · 博客'
    case 'a2a': return '文枢 · A2A 节点'
    case 'flashcard': return '文枢 · 闪卡'
    case 'flashcard-detail': return '闪卡详情'
    case 'flashcard-study': return '翻看卡片'
    case 'admin':
    case 'admin-posts':
      switch (adminTab.value) {
        case 'posts': return '文章管理'
        case 'knowledge': return '知识库管理'
        case 'agents': return '智能体管理'
        case 'settings': return '系统设置'
        case 'categories': return '分类管理'
        case 'media': return '媒体库'
        default: return '管理后台'
      }
    case 'admin-login': return '管理后台'
    default: return ''
  }
})

function switchPage(page: string) {
  if (page === 'a2a') {
    currentPage.value = 'a2a'
    fetchTasks()
  } else if (page === 'blog') {
    currentPage.value = 'blog'
    currentArticle.value = null
  } else {
    currentPage.value = page
  }
}

function handleAdminClick() {
  if (isLoggedIn.value) {
    currentPage.value = 'admin'
    adminTab.value = 'posts'
  } else {
    // 检查是否需要密码，无密码则自动登录
    checkAuthStatus().then(enabled => {
      if (!enabled) {
        loginNoPassword().then(ok => {
          if (ok) {
            currentPage.value = 'admin'
            adminTab.value = 'posts'
          } else {
            currentPage.value = 'admin-login'
          }
        })
      } else {
        currentPage.value = 'admin-login'
      }
    })
  }
}

async function openArticle(slug: string) {
  currentArticle.value = await getPost(slug)
  currentPage.value = 'blog-detail'
}

async function handleAdminLogin() {
  adminLoginError.value = false
  const ok = await login(adminPassword.value)
  if (ok) {
    adminPassword.value = ''
    currentPage.value = 'admin'
    adminTab.value = 'posts'
  } else {
    adminLoginError.value = true
  }
}

function handleLogout() {
  logout()
  currentPage.value = 'chat'
}

function openEditor(article?: Article) {
  editingArticle.value = article || null
  currentPage.value = 'admin-editor'
  adminTab.value = 'posts'
}

function onArticleSaved(article: Article) {
  editingArticle.value = article
  currentPage.value = 'admin'
  adminTab.value = 'posts'
}

async function checkHealth() {
  try {
    const res = await fetch('/api/health')
    if (res.ok) {
      healthStatus.value = (await res.json()).status
    } else {
      healthStatus.value = 'error'
    }
  } catch {
    healthStatus.value = 'disconnected'
  }
}

onMounted(async () => {
  checkHealth()
  fetchAgentCard()
  const res = await fetch('/api/knowledge/stats')
  if (res.ok) stats.value = await res.json()
  fetchTasks()
  loadConversations()
  loadAgents()
})
</script>

<style>
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap');

* { box-sizing: border-box; }

body {
  margin: 0;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  background: #FAF7F2;
  color: #3D3028;
}

::-webkit-scrollbar { width: 6px; }
::-webkit-scrollbar-track { background: transparent; }
::-webkit-scrollbar-thumb { background: #D4C8BA; border-radius: 3px; }
::-webkit-scrollbar-thumb:hover { background: #B8A898; }

.app-shell { display: flex; height: 100vh; overflow: hidden; }

/* Sidebar */
.sidebar {
  width: 64px;
  background: #3D3028;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16px 0;
  flex-shrink: 0;
}
.sidebar-logo {
  width: 40px; height: 40px;
  border-radius: 12px;
  overflow: hidden;
  margin-bottom: 32px;
}
.sidebar-logo img {
  width: 100%; height: 100%;
  object-fit: cover;
  border-radius: 12px;
}
.sidebar-nav { display: flex; flex-direction: column; gap: 8px; flex: 1; }
.nav-btn {
  width: 44px; height: 44px;
  border: none; border-radius: 12px;
  background: transparent; color: #A89888;
  cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  transition: all 0.2s;
}
.nav-btn:hover { background: rgba(232,145,58,0.15); color: #E8913A; }
.nav-btn.active { background: rgba(232,145,58,0.2); color: #E8913A; }
.sidebar-status { margin-top: auto; }
.status-dot { display: block; width: 10px; height: 10px; border-radius: 50%; }
.status-dot.ok { background: #6ABF69; }
.status-dot.err { background: #E85D5D; }

/* Main Area */
.main-area { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.topbar {
  height: 56px;
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 24px;
  border-bottom: 1px solid #E8DDD0;
  background: #FFFFFF;
  flex-shrink: 0;
}
.topbar-title { font-size: 17px; font-weight: 600; color: #3D3028; margin: 0; }
.topbar-actions { display: flex; align-items: center; gap: 12px; }
.topbar-info { font-size: 13px; color: #A89888; }
.new-chat-btn {
  display: flex; align-items: center; gap: 6px;
  padding: 6px 14px; border-radius: 8px;
  border: 1px solid #E8DDD0; background: #FFFFFF;
  color: #D97B2B; font-size: 13px; font-weight: 500;
  cursor: pointer; transition: all 0.2s;
}
.new-chat-btn:hover { background: #FEF3E8; border-color: #D97B2B; }

/* Content */
.content-wrapper { flex: 1; display: flex; overflow: hidden; min-height: 0; }
.chat-area { flex: 1; overflow: hidden; }
.a2a-page { flex: 1; overflow-y: auto; overflow-x: hidden; background: #F8F5EF; }
.a2a-container { max-width: 860px; margin: 0 auto; padding: 24px 32px; }

/* A2A Hero */
.a2a-hero {
  background: #FFFFFF;
  border: 1px solid #E8DDD0;
  border-radius: 14px;
  overflow: hidden;
  margin-bottom: 20px;
}
.hero-accent { height: 4px; background: linear-gradient(90deg, #E8913A, #D4644A, #E8913A); }
.hero-body { padding: 24px 28px; }
.hero-top { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.hero-status { display: flex; align-items: center; gap: 6px; font-size: 12px; font-weight: 500; color: #4CAF50; }
.hero-dot { width: 8px; height: 8px; border-radius: 50%; background: #4CAF50; box-shadow: 0 0 6px rgba(76,175,80,0.4); }
.hero-version { font-size: 12px; color: #B8A898; background: #F5F0EB; padding: 2px 10px; border-radius: 10px; font-family: 'Cascadia Code', 'Fira Code', monospace; }
.hero-name { font-size: 22px; font-weight: 700; color: #3D3028; margin: 0 0 8px; }
.hero-desc { font-size: 14px; color: #6B5E52; line-height: 1.6; margin: 0 0 16px; }
.hero-endpoint { display: flex; align-items: center; gap: 8px; background: #FAF7F2; border: 1px solid #E8DDD0; border-radius: 8px; padding: 8px 12px; margin-bottom: 16px; }
.hero-endpoint svg { color: #B8A898; flex-shrink: 0; }
.hero-endpoint code { font-family: 'Cascadia Code', 'Fira Code', monospace; font-size: 12px; color: #6B5E52; }
.hero-tags { display: flex; flex-wrap: wrap; gap: 8px; }
.hero-skill { display: inline-flex; align-items: center; padding: 4px 14px; border-radius: 14px; font-size: 12px; font-weight: 500; background: linear-gradient(135deg, #FEF3E8, #FFF5ED); color: #D97B2B; border: 1px solid #F5DFC8; }
.hero-cap { display: inline-flex; align-items: center; padding: 4px 14px; border-radius: 14px; font-size: 12px; font-weight: 500; }
.hero-cap.on { background: #E8F5E9; color: #4CAF50; border: 1px solid #C8E6C9; }
.hero-cap.off { background: #F5F0EB; color: #B8A898; border: 1px solid #E8DDD0; }
.a2a-hero-offline .hero-body { display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 40px 28px; }

/* Side Panel */
.side-panel {
  width: 320px;
  border-left: 1px solid #E8DDD0;
  background: #FFFFFF;
  display: flex; position: relative;
  flex-shrink: 0;
  transition: width 0.25s ease;
}
.side-panel.collapsed { width: 0; border-left: none; }
.side-panel-toggle {
  position: absolute; left: -16px; top: 50%; transform: translateY(-50%);
  width: 24px; height: 48px;
  border: 1px solid #E8DDD0; border-radius: 8px 0 0 8px;
  background: #FFFFFF; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  color: #A89888; font-size: 12px; z-index: 10;
}
.side-panel-toggle:hover { background: #FAF7F2; color: #E8913A; }
.side-panel-content { width: 320px; overflow-y: auto; padding: 16px 12px; }
.sidebar-divider { width: 28px; height: 1px; background: #5A4E44; margin: 8px 0; }

/* Admin Login */
.admin-login { flex: 1; display: flex; align-items: center; justify-content: center; background: #F8F5EF; }
.login-card { background: #FFFFFF; border: 1px solid #E8DDD0; border-radius: 16px; padding: 40px; width: 360px; text-align: center; }
.login-title { font-size: 20px; font-weight: 600; color: #3D3028; margin: 0 0 8px; }
.login-desc { font-size: 14px; color: #8B7E74; margin: 0 0 24px; }
.login-input { width: 100%; padding: 10px 16px; border: 1px solid #E8DDD0; border-radius: 10px; font-size: 14px; color: #3D3028; outline: none; margin-bottom: 8px; box-sizing: border-box; }
.login-input:focus { border-color: #D97B2B; }
.login-error { font-size: 13px; color: #E85D5D; margin: 0 0 12px; }
.login-btn { width: 100%; padding: 10px; border: none; border-radius: 10px; background: #D97B2B; color: #FFFFFF; font-size: 14px; font-weight: 500; cursor: pointer; transition: background 0.2s; }
.login-btn:hover { background: #C06A1E; }

/* Admin Panel */
.admin-panel { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.admin-tabs {
  display: flex; align-items: center;
  padding: 0 20px; border-bottom: 1px solid #E8DDD0;
  background: #FFFFFF; height: 46px; flex-shrink: 0;
  gap: 2px;
}
.admin-tab-btn {
  display: inline-flex; align-items: center; gap: 6px;
  border: none; background: none; color: #8B7E74;
  font-size: 13px; font-weight: 500; cursor: pointer;
  padding: 12px 14px; border-bottom: 2px solid transparent;
  transition: all 0.2s; white-space: nowrap;
}
.admin-tab-btn:hover { color: #D97B2B; }
.admin-tab-btn.active { color: #D97B2B; border-bottom-color: #D97B2B; }
.admin-tabs-spacer { flex: 1; }
.admin-logout-btn {
  display: inline-flex; align-items: center; gap: 5px;
  border: 1px solid #E8DDD0; border-radius: 6px; background: #FFFFFF;
  color: #8B7E74; font-size: 12px; padding: 4px 10px; cursor: pointer;
  transition: all 0.2s;
}
.admin-logout-btn:hover { border-color: #E85D5D; color: #E85D5D; }
.admin-content { flex: 1; overflow-y: auto; overflow-x: hidden; }
</style>
