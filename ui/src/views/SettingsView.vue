<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Dialog, Toast, VButton, VCard, VPageHeader } from '@halo-dev/components'
import { utils } from '@halo-dev/ui-shared'
import { downloadBackupZip, getApiErrorMessage, getData, importBackupZip, putData } from '@/api/client'
import RiSettings3Line from '~icons/ri/settings-3-line'

type AccessMode = 'none' | 'password'

type SettingsResp = {
  basic: {
    accessMode: AccessMode
    accessPassword: string
    accessPasswordSet: boolean
    publicAccessUrl: string
    publicLogoUrl: string
    /** 浏览器标签标题 */
    publicSiteTitle: string
    /** 前台顶栏主标题 */
    publicBrandTitle: string
    /** 前台顶栏副标题 */
    publicBrandSubtitle: string
    publicDomainWhitelist: string
    defaultPublicAccessUrl: string
  }
  notify: {
    enabled: boolean
    channel: string
    webhookUrl: string
    orderNowEnabled: boolean
    orderReservationEnabled: boolean
  }
}

const loading = ref(true)
const saving = ref(false)
const activeTab = ref<'basic' | 'security' | 'notify' | 'backup'>('basic')
const logoAttachmentModalOpen = ref(false)

const backupIncludeOrders = ref(false)
const backupExporting = ref(false)
const backupImporting = ref(false)
const backupFileInput = ref<HTMLInputElement | null>(null)

const form = reactive<SettingsResp>({
  basic: {
    accessMode: 'none',
    accessPassword: '',
    accessPasswordSet: false,
    publicAccessUrl: '',
    publicLogoUrl: '',
    publicSiteTitle: '',
    publicBrandTitle: '',
    publicBrandSubtitle: '',
    publicDomainWhitelist: '',
    defaultPublicAccessUrl: '/dishes',
  },
  notify: {
    enabled: false,
    channel: '',
    webhookUrl: '',
    orderNowEnabled: true,
    orderReservationEnabled: false,
  },
})

const accessModeOptions: Array<{ value: AccessMode; label: string; desc: string }> = [
  { value: 'none', label: '无需鉴权', desc: '前台可直接访问并点菜，无需登录或密码。' },
  { value: 'password', label: '密码访问', desc: '首次访问需输入密码，本地缓存有效期内免重复输入。' },
]

const needPasswordInput = computed(() => form.basic.accessMode === 'password')
const logoPreviewUrl = computed(() => {
  const raw = (form.basic.publicLogoUrl || '').trim()
  if (!raw) return ''
  return utils.attachment.getThumbnailUrl(raw, 'M')
})

type AttachmentLike = {
  status?: Record<string, unknown>
  spec?: Record<string, unknown>
  permalink?: string
  url?: string
  path?: string
}

function asString(v: unknown) {
  return typeof v === 'string' ? v : ''
}

function onLogoAttachmentSelect(attachments: AttachmentLike[]) {
  const first = attachments?.[0]
  const status = first?.status ?? {}
  const spec = first?.spec ?? {}
  const url =
    asString(status.permalink) ||
    asString(status.url) ||
    asString(status.link) ||
    asString(status.publicUrl) ||
    asString(status.downloadUrl) ||
    asString(spec.permalink) ||
    asString(spec.url) ||
    asString(spec.link) ||
    asString(spec.path) ||
    asString(first?.permalink) ||
    asString(first?.url) ||
    asString(first?.path) ||
    ''

  if (url.trim()) {
    form.basic.publicLogoUrl = url.trim()
    return
  }
  Dialog.warning({ title: '选择失败', description: '未从附件中解析到可用图片地址，请换一个附件重试。' })
}

async function load() {
  loading.value = true
  try {
    const data = await getData<SettingsResp>('/settings')
    form.basic.accessMode = data.basic?.accessMode ?? 'none'
    form.basic.accessPassword = data.basic?.accessPassword ?? ''
    form.basic.accessPasswordSet = !!data.basic?.accessPasswordSet
    form.basic.defaultPublicAccessUrl = data.basic?.defaultPublicAccessUrl ?? '/dishes'
    form.basic.publicAccessUrl = data.basic?.publicAccessUrl || form.basic.defaultPublicAccessUrl
    form.basic.publicLogoUrl = data.basic?.publicLogoUrl ?? ''
    form.basic.publicSiteTitle = data.basic?.publicSiteTitle ?? ''
    form.basic.publicBrandTitle = data.basic?.publicBrandTitle ?? ''
    form.basic.publicBrandSubtitle = data.basic?.publicBrandSubtitle ?? ''
    form.basic.publicDomainWhitelist = data.basic?.publicDomainWhitelist ?? ''
    form.notify.enabled = !!data.notify?.enabled
    form.notify.channel = data.notify?.channel ?? ''
    form.notify.webhookUrl = data.notify?.webhookUrl ?? ''
    form.notify.orderNowEnabled = data.notify?.orderNowEnabled ?? true
    form.notify.orderReservationEnabled = data.notify?.orderReservationEnabled ?? false
  } catch (e) {
    Dialog.error({
      title: '加载失败',
      description: getApiErrorMessage(e, '加载设置失败，请稍后重试', {
        DISHES_ACCESS_DENIED: '当前账号无权访问该设置',
      }),
    })
  } finally {
    loading.value = false
  }
}

async function save() {
  if (needPasswordInput.value && !form.basic.accessPassword.trim()) {
    Dialog.warning({ title: '提示', description: '密码访问模式下请先填写前台访问密码。' })
    return
  }
  saving.value = true
  try {
    await putData('/settings', {
      basic: {
        accessMode: form.basic.accessMode,
        accessPassword: form.basic.accessPassword,
        publicAccessUrl: form.basic.publicAccessUrl,
        publicLogoUrl: form.basic.publicLogoUrl,
        publicSiteTitle: form.basic.publicSiteTitle,
        publicBrandTitle: form.basic.publicBrandTitle,
        publicBrandSubtitle: form.basic.publicBrandSubtitle,
        publicDomainWhitelist: form.basic.publicDomainWhitelist,
      },
      notifyConfig: {
        enabled: form.notify.enabled,
        channel: form.notify.channel,
        webhookUrl: form.notify.webhookUrl,
        orderNowEnabled: form.notify.orderNowEnabled,
        orderReservationEnabled: form.notify.orderReservationEnabled,
      },
    })
    Toast.success('设置已保存')
    await load()
  } catch (e) {
    Dialog.error({
      title: '保存失败',
      description: getApiErrorMessage(e, '保存失败，请稍后重试', {
        DISHES_BAD_REQUEST: '参数校验失败，请检查配置后重试',
      }),
    })
  } finally {
    saving.value = false
  }
}

async function exportDishesBackup() {
  backupExporting.value = true
  try {
    const blob = await downloadBackupZip({ includeOrders: backupIncludeOrders.value })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `dishes-backup-${new Date().toISOString().slice(0, 10)}.zip`
    a.rel = 'noopener'
    a.click()
    URL.revokeObjectURL(url)
    Toast.success('导出已开始下载')
  } catch (e) {
    Dialog.error({
      title: '导出失败',
      description: getApiErrorMessage(e, '导出失败，请稍后重试'),
    })
  } finally {
    backupExporting.value = false
  }
}

function pickBackupFile() {
  backupFileInput.value?.click()
}

async function onBackupFileChange(ev: Event) {
  const input = ev.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  const name = file.name.toLowerCase()
  if (!name.endsWith('.zip')) {
    Dialog.warning({ title: '格式不正确', description: '请选择本插件导出的 .zip 备份文件。' })
    return
  }
  const ok = window.confirm(
    '覆盖式导入将删除当前所有点餐订单、菜品与分类，并以备份文件重建。\n\n导入前请确认已另行保存必要数据。是否继续？',
  )
  if (!ok) return
  backupImporting.value = true
  try {
    const r = await importBackupZip(file)
    Toast.success(`导入完成：分类 ${r.imported_categories}，菜品 ${r.imported_dishes}，订单 ${r.imported_orders}`)
    await load()
  } catch (e) {
    Dialog.error({
      title: '导入失败',
      description: getApiErrorMessage(e, '导入失败，请检查备份文件或稍后重试', {
        DISHES_BAD_REQUEST: '备份文件无效或与当前版本不兼容',
      }),
    })
  } finally {
    backupImporting.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <VPageHeader title="插件设置">
    <template #icon>
      <RiSettings3Line />
    </template>
  </VPageHeader>

  <div class="settings-page :uno: p-4">
    <AttachmentSelectorModal
      v-model:visible="logoAttachmentModalOpen"
      :max="1"
      :accepts="['image/*']"
      @select="onLogoAttachmentSelect"
    />

    <VCard class="settings-card" :body-class="[':uno: !p-0']">
      <template #header>
        <div class="settings-tabs">
          <button
            type="button"
            class="settings-tab"
            :class="{ 'settings-tab--active': activeTab === 'basic' }"
            @click="activeTab = 'basic'"
          >
            基础设置
          </button>
          <button
            type="button"
            class="settings-tab"
            :class="{ 'settings-tab--active': activeTab === 'security' }"
            @click="activeTab = 'security'"
          >
            安全设置
          </button>
          <button
            type="button"
            class="settings-tab"
            :class="{ 'settings-tab--active': activeTab === 'notify' }"
            @click="activeTab = 'notify'"
          >
            通知设置
          </button>
          <button
            type="button"
            class="settings-tab"
            :class="{ 'settings-tab--active': activeTab === 'backup' }"
            @click="activeTab = 'backup'"
          >
            数据备份
          </button>
        </div>
      </template>

      <div class="settings-body">
        <div v-if="loading" class="settings-loading">加载中…</div>

        <template v-else>
          <div v-show="activeTab === 'basic'" class="settings-section">
            <h3 class="settings-title">前台展示</h3>
            <p class="settings-desc">Logo 与标题文案将显示在家庭点菜前台顶栏及浏览器标签。</p>

            <div class="settings-field">
              <div class="settings-field-label">前台 Logo</div>
              <div class="settings-logo-row">
                <div class="settings-logo-preview-wrap">
                  <img v-if="logoPreviewUrl" :src="logoPreviewUrl" alt="logo" class="settings-logo-preview" />
                  <span v-else class="settings-logo-placeholder">未设置</span>
                </div>
                <div class="settings-logo-actions">
                  <VButton size="sm" @click="logoAttachmentModalOpen = true">从附件库选择</VButton>
                  <VButton size="sm" type="secondary" :disabled="!form.basic.publicLogoUrl" @click="form.basic.publicLogoUrl = ''">
                    清空
                  </VButton>
                </div>
              </div>
              <p class="settings-hint">未设置时前台将继续使用默认图标。</p>
            </div>

            <div class="settings-field">
              <div class="settings-field-label">前台页面标题</div>
              <input
                v-model="form.basic.publicSiteTitle"
                type="text"
                class="settings-input"
                maxlength="80"
                placeholder="浏览器标签显示，例如：柠檬私厨"
              />
              <p class="settings-hint">留空则使用默认「柠檬私厨」。路由切换时会显示为「首页 · 标题」等形式。</p>
            </div>

            <div class="settings-field">
              <div class="settings-field-label">前台顶栏主标题</div>
              <input
                v-model="form.basic.publicBrandTitle"
                type="text"
                class="settings-input"
                maxlength="60"
                placeholder="例如：柠檬私厨"
              />
              <p class="settings-hint">留空则使用默认「柠檬私厨」。</p>
            </div>

            <div class="settings-field">
              <div class="settings-field-label">前台顶栏副标题</div>
              <textarea
                v-model="form.basic.publicBrandSubtitle"
                class="settings-textarea"
                maxlength="400"
                rows="3"
                placeholder="例如：今天，想吃点什么？"
              />
              <p class="settings-hint">留空则使用默认「今天，想吃点什么？」（最多 400 字，保存时服务端会截断）。</p>
            </div>
          </div>

          <div v-show="activeTab === 'security'" class="settings-section">
            <h3 class="settings-title">安全设置</h3>
            <p class="settings-desc">控制前台访问鉴权方式、入口路径与跨域来源限制。</p>

            <h4 class="settings-subtitle">前台访问策略</h4>
            <div class="settings-mode-list">
              <label v-for="opt in accessModeOptions" :key="opt.value" class="settings-mode-item">
                <input v-model="form.basic.accessMode" type="radio" name="access-mode" :value="opt.value" />
                <div class="settings-mode-content">
                  <div class="settings-mode-label">{{ opt.label }}</div>
                  <div class="settings-mode-desc">{{ opt.desc }}</div>
                </div>
              </label>
            </div>

            <div v-if="needPasswordInput" class="settings-field">
              <div class="settings-field-label">前台访问密码</div>
              <input
                v-model="form.basic.accessPassword"
                type="password"
                class="settings-input"
                placeholder="请输入前台访问密码"
              />
              <p v-if="form.basic.accessPasswordSet" class="settings-hint">已设置密码，留空将清空。</p>
            </div>

            <div class="settings-field">
              <div class="settings-field-label">自定义前台访问链接</div>
              <input
                v-model="form.basic.publicAccessUrl"
                type="text"
                class="settings-input"
                placeholder="例如：/dishes"
              />
              <p class="settings-hint">
                当前默认路径：{{ form.basic.defaultPublicAccessUrl }}；留空则使用默认前台入口链接。
              </p>
            </div>

            <div class="settings-field">
              <div class="settings-field-label">前台独立域名白名单</div>
              <textarea
                v-model="form.basic.publicDomainWhitelist"
                class="settings-textarea"
                rows="4"
                placeholder="每行一个域名，如：menu.example.com"
              />
              <p class="settings-hint">
                留空表示不限制来源；填写一级域名则其二级域名也允许请求。
              </p>
            </div>
          </div>

          <div v-show="activeTab === 'notify'" class="settings-section">
            <h3 class="settings-title">通知设置</h3>
            <p class="settings-desc">点菜提交后可推送企业微信群机器人消息卡片。</p>
            <label class="settings-checkbox">
              <input v-model="form.notify.enabled" type="checkbox" />
              <span>启用通知</span>
            </label>
            <div class="settings-field">
              <div class="settings-field-label">企业微信群机器人 Webhook</div>
              <input
                v-model="form.notify.webhookUrl"
                type="text"
                class="settings-input"
                placeholder="https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=..."
              />
            </div>
            <label class="settings-checkbox">
              <input v-model="form.notify.orderNowEnabled" type="checkbox" :disabled="!form.notify.enabled" />
              <span>立即点菜通知</span>
            </label>
            <label class="settings-checkbox">
              <input v-model="form.notify.orderReservationEnabled" type="checkbox" :disabled="!form.notify.enabled" />
              <span>预约点菜通知</span>
            </label>
          </div>

          <div v-show="activeTab === 'backup'" class="settings-section">
            <input
              ref="backupFileInput"
              type="file"
              accept=".zip,application/zip"
              class="settings-hidden-file"
              @change="onBackupFileChange"
            />
            <h3 class="settings-title">数据备份</h3>
            <p class="settings-desc">
              导出为 ZIP，内含 <code class="settings-code">manifest.json</code>。菜品仅备份 <code class="settings-code">image_url</code> 路径或地址，不包含图片文件本身；跨站迁移时请自行保证附件或外链可访问。
            </p>
            <label class="settings-checkbox">
              <input v-model="backupIncludeOrders" type="checkbox" />
              <span>导出时包含点餐记录</span>
            </label>
            <div class="settings-backup-actions">
              <VButton :disabled="loading || backupExporting || backupImporting" @click="exportDishesBackup">
                {{ backupExporting ? '导出中…' : '导出备份（ZIP）' }}
              </VButton>
            </div>

            <div class="settings-divider" />

            <h4 class="settings-subtitle">覆盖式导入</h4>
            <p class="settings-hint settings-hint--danger">
              将删除当前<strong>全部</strong>点餐订单、菜品与分类，再以备份文件重建。执行前请自行导出或确认无需保留现有数据。
            </p>
            <div class="settings-backup-actions settings-backup-actions--import">
              <VButton
                :disabled="loading || backupExporting || backupImporting"
                class="settings-import-btn"
                @click="pickBackupFile"
              >
                {{ backupImporting ? '导入中…' : '选择 ZIP 并导入' }}
              </VButton>
            </div>
          </div>
        </template>
      </div>

      <template #footer>
        <div class="settings-footer">
          <VButton :disabled="loading || saving" @click="load">重置</VButton>
          <VButton type="primary" :disabled="loading || saving" @click="save">保存</VButton>
        </div>
      </template>
    </VCard>
  </div>
</template>

<style scoped>
.settings-card {
  box-shadow: none;
}

.settings-card :deep(.card-header),
.settings-card :deep(.card-footer) {
  padding: 0;
}

.settings-card :deep(.card-header) {
  background: var(--ls-dishes-surface, #fff);
}

.settings-tabs {
  display: flex;
  overflow-x: auto;
  border-bottom: 1px solid var(--ls-dishes-line, #e8e8eb);
  background: var(--ls-dishes-surface, #fff);
}

.settings-tab {
  border: none;
  background: transparent;
  flex: 0 0 auto;
  min-height: 48px;
  padding: 16px;
  font-size: 0.875rem;
  color: var(--ls-dishes-muted, #6b6b75);
  cursor: pointer;
}

.settings-tab--active {
  color: var(--ls-dishes-gold, #946d12);
  font-weight: 500;
  box-shadow: inset 0 -2px 0 var(--ls-dishes-highlight, #e7b63e);
}

.settings-tab:focus-visible {
  outline: 2px solid var(--ls-dishes-gold, #946d12);
  outline-offset: -4px;
}

.settings-body {
  padding: 1rem;
}

.settings-loading {
  color: var(--ls-dishes-muted, #6b6b75);
  font-size: 0.875rem;
  padding: 0.5rem 0;
}

.settings-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.settings-title {
  margin: 0;
  font-size: 1rem;
  font-weight: 600;
  color: var(--ls-dishes-ink, #29292e);
}

.settings-subtitle {
  margin: 0.25rem 0 0;
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--ls-dishes-ink, #29292e);
}

.settings-desc {
  margin: 0;
  font-size: 0.875rem;
  color: var(--ls-dishes-muted, #6b6b75);
}

.settings-mode-list {
  display: flex;
  flex-direction: column;
  gap: 0.625rem;
}

.settings-mode-item {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  border: 1px solid var(--ls-dishes-line, #e8e8eb);
  border-radius: 8px;
  padding: 0.625rem 0.75rem;
}

.settings-mode-content {
  min-width: 0;
}

.settings-mode-label {
  font-size: 0.875rem;
  font-weight: 600;
}

.settings-mode-desc {
  font-size: 0.8125rem;
  color: var(--ls-dishes-muted, #6b6b75);
}

.settings-field {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}

.settings-logo-row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.settings-logo-preview-wrap {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  border: 1px solid rgb(212 212 216);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: #fff;
}

.settings-logo-preview {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.settings-logo-placeholder {
  font-size: 10px;
  color: #6b6b75;
}

.settings-logo-actions {
  display: inline-flex;
  gap: 0.5rem;
}

.settings-field-label {
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--ls-dishes-ink, #29292e);
}

.settings-input {
  height: 2.25rem;
  border: 1px solid rgb(212 212 216);
  border-radius: 6px;
  padding: 0 0.75rem;
  font-size: 0.875rem;
  border-color: var(--ls-dishes-line, #e8e8eb);
  background: var(--ls-dishes-surface, #fff);
  color: var(--ls-dishes-ink, #29292e);
}

.settings-textarea {
  border: 1px solid rgb(212 212 216);
  border-radius: 6px;
  padding: 0.5rem 0.75rem;
  font-size: 0.875rem;
  resize: vertical;
  min-height: 88px;
  border-color: var(--ls-dishes-line, #e8e8eb);
  background: var(--ls-dishes-surface, #fff);
  color: var(--ls-dishes-ink, #29292e);
}

.settings-hint {
  margin: 0;
  font-size: 0.75rem;
  color: var(--ls-dishes-muted, #6b6b75);
}

.settings-checkbox {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.875rem;
}

.settings-footer {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
}

.settings-hidden-file {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.settings-code {
  font-size: 0.8125rem;
  padding: 0.1rem 0.35rem;
  border-radius: 4px;
  background: var(--ls-dishes-soft, #f7f7f8);
  color: var(--ls-dishes-ink, #29292e);
}

.settings-backup-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  align-items: center;
}

.settings-divider {
  height: 1px;
  margin: 0.75rem 0;
  background: var(--ls-dishes-line, #e8e8eb);
}

.settings-hint--danger {
  color: var(--ls-dishes-danger, #b91c1c);
}

.settings-backup-actions--import :deep(.settings-import-btn),
.settings-backup-actions--import :deep(.settings-import-btn button),
.settings-backup-actions--import :deep(button.settings-import-btn) {
  background-color: rgb(24 24 27) !important;
  color: rgb(250 250 250) !important;
  border-color: rgb(24 24 27) !important;
  box-shadow: none !important;
  font-weight: 500;
}

.settings-backup-actions--import :deep(.settings-import-btn:hover:not(:disabled)),
.settings-backup-actions--import :deep(.settings-import-btn button:hover:not(:disabled)) {
  background-color: rgb(39 39 42) !important;
  color: rgb(250 250 250) !important;
  border-color: rgb(39 39 42) !important;
}

.settings-backup-actions--import :deep(.settings-import-btn:focus-visible),
.settings-backup-actions--import :deep(.settings-import-btn button:focus-visible) {
  outline: 2px solid rgb(24 24 27);
  outline-offset: 2px;
}

.settings-backup-actions--import :deep(.settings-import-btn:disabled),
.settings-backup-actions--import :deep(.settings-import-btn button:disabled) {
  opacity: 0.55;
  cursor: not-allowed;
}
</style>
