
function apiBase(): string {
  return ''
}

function apiPrefix(): string {
  const raw = import.meta.env.VITE_API_PREFIX ?? '/plugins/dishes/public'
  const s = typeof raw === 'string' ? raw : '/plugins/dishes/public'
  return `/${s}`.replace(/\/+$/, '').replace(/\/{2,}/g, '/')
}

const ACCESS_TOKEN_KEY = 'dishes_access_token'
export class ApiError extends Error {
  readonly code?: string
  readonly status?: number

  constructor(message: string, code?: string, status?: number) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
  }
}

type ErrorCodeMessageMap = Partial<Record<string, string>>

function accessToken(): string {
  try {
    return localStorage.getItem(ACCESS_TOKEN_KEY) ?? ''
  } catch {
    return ''
  }
}

export function setAccessToken(token: string) {
  try {
    if (token.trim()) localStorage.setItem(ACCESS_TOKEN_KEY, token.trim())
    else localStorage.removeItem(ACCESS_TOKEN_KEY)
  } catch {
    // ignore
  }
}

function withAccessHeaders(init: HeadersInit): HeadersInit {
  const token = accessToken()
  if (!token) return init
  return {
    ...init,
    'X-Dishes-Access-Token': token,
  }
}

async function loadCsrfMetadata(): Promise<{ headerName: string; token: string }> {
  const res = await fetch(`${apiBase()}${apiPrefix()}/csrf-metadata`, {
    headers: withAccessHeaders({ Accept: 'application/json' }),
    credentials: 'include',
  })
  const { json, parseNote } = await readJsonBody(res)
  if (!res.ok || !isOkEnvelope(json)) throw toApiError(res, json, parseNote)
  const data = (json as { data: { headerName?: string; token?: string } }).data
  if (!data?.headerName || !data.token) {
    throw new ApiError('无法获取验证信息，请允许本站 Cookie 后重试。', 'CSRF_MISSING')
  }
  return { headerName: data.headerName, token: data.token }
}

export async function apiGet<T>(path: string): Promise<T> {
  const base = apiBase()
  const prefix = apiPrefix()
  const res = await fetch(`${base}${prefix}${path}`, {
    headers: withAccessHeaders({ Accept: 'application/json' }),
    credentials: 'include',
  })
  const { json, parseNote } = await readJsonBody(res)

  if (!res.ok || !isOkEnvelope(json)) {
    throw toApiError(res, json, parseNote)
  }

  return (json as { data: T }).data
}

export async function apiPost<T>(path: string, body: unknown): Promise<T> {
  const base = apiBase()
  const prefix = apiPrefix()
  const csrf = await loadCsrfMetadata()
  const headers: HeadersInit = withAccessHeaders({
    Accept: 'application/json',
    'Content-Type': 'application/json',
    [csrf.headerName]: csrf.token,
  })
  const res = await fetch(`${base}${prefix}${path}`, {
    method: 'POST',
    headers,
    body: JSON.stringify(body),
    credentials: 'include',
  })
  const { json, parseNote } = await readJsonBody(res)

  if (!res.ok || !isOkEnvelope(json)) {
    throw toApiError(res, json, parseNote)
  }

  return (json as { data: T }).data
}

function isOkEnvelope(v: unknown): v is { ok: true; data: unknown } {
  return typeof v === 'object' && v !== null && (v as { ok?: unknown }).ok === true
}

async function readJsonBody(res: Response): Promise<{
  json: unknown
  parseNote?: string
}> {
  const text = await res.text()
  if (!text.trim()) {
    return { json: {}, parseNote: '响应体为空' }
  }
  try {
    return { json: JSON.parse(text) as unknown }
  } catch {
    const hint =
      text.trimStart().startsWith('<') || text.includes('<!DOCTYPE')
        ? '收到 HTML 而非 JSON，多为未登录或接口无匿名访问权限'
        : '响应不是合法 JSON'
    return { json: {}, parseNote: hint }
  }
}

function resolveApiErrorMessage(res: Response, json: unknown, parseNote?: string): string {
  if (
    typeof json === 'object' &&
    json !== null &&
    'message' in json &&
    typeof (json as { message: unknown }).message === 'string'
  ) {
    return (json as { message: string }).message
  }
  if (parseNote) {
    return parseNote
  }
  return res.statusText || '请求失败'
}

function resolveApiErrorCode(json: unknown): string | undefined {
  if (
    typeof json === 'object' &&
    json !== null &&
    'code' in json &&
    typeof (json as { code: unknown }).code === 'string'
  ) {
    return (json as { code: string }).code
  }
  return undefined
}

function toApiError(res: Response, json: unknown, parseNote?: string): ApiError {
  return new ApiError(resolveApiErrorMessage(res, json, parseNote), resolveApiErrorCode(json), res.status)
}

export function getApiErrorMessage(
  error: unknown,
  fallback: string,
  codeMessageMap?: ErrorCodeMessageMap,
): string {
  if (error instanceof ApiError) {
    if (error.code && codeMessageMap?.[error.code]) return codeMessageMap[error.code] as string
    return error.message || fallback
  }
  if (error instanceof Error) return error.message || fallback
  return fallback
}
