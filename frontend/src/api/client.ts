import { env } from '@/config/env'
import { ApiError, type PageResponse, type ProblemDetail } from '@/types/api'

/**
 * The access token lives only in memory (decisiones.md #125) - never localStorage. AuthProvider
 * is the only caller of setAccessToken, right after OAuth2 login and after each refresh.
 */
let accessToken: string | null = null

/**
 * Stores the access token every later request is signed with, or clears it on logout.
 *
 * The token lives **in memory only** - never `localStorage`, never `sessionStorage` (#125). The app
 * renders rich text written by other people (#62), which is the most direct XSS surface it has, and
 * a token in web storage is readable by any script that gets in through it. The refresh token is not
 * here at all: it is an httpOnly cookie the frontend can neither read nor write.
 *
 * @param token the new access token, or null to forget the current one
 */
export function setAccessToken(token: string | null): void {
  accessToken = token
}

type QueryParams = Record<string, string | number | boolean | undefined>

interface RequestOptions {
  params?: QueryParams | undefined
  body?: unknown
  files?: File[] | undefined
}

function buildUrl(path: string, params?: QueryParams): string {
  const url = new URL(path, env.apiBaseUrl)
  if (params) {
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined) {
        url.searchParams.set(key, String(value))
      }
    }
  }
  return url.toString()
}

function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`))
  const value = match?.[1]
  return value !== undefined ? decodeURIComponent(value) : null
}

/**
 * Only /auth/refresh needs the CSRF header - it is the one endpoint authenticated by cookie
 * (decisiones.md #127). Exported so AuthProvider can call it once on boot to recover a session
 * from the httpOnly refresh cookie, without a chicken-and-egg 401 first.
 */
export async function refreshSession(): Promise<string | null> {
  const csrfToken = readCookie('XSRF-TOKEN')
  const response = await fetch(buildUrl('/api/v1/auth/refresh'), {
    method: 'POST',
    credentials: 'include',
    headers: csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {},
  })
  if (!response.ok) {
    return null
  }
  const data = (await response.json()) as { accessToken: string }
  setAccessToken(data.accessToken)
  return data.accessToken
}

/**
 * Sends the call and settles the 401 - refresh once, retry, and give up to /login if that fails too.
 *
 * Split out of `request` so a download can share it. A binary response is not JSON and must not be
 * parsed as any, but everything up to the body - the bearer token, the credentials, the refresh
 * dance - is the same, and having two copies of the 401 handling is how they drift apart.
 */
async function send(method: string, path: string, options: RequestOptions = {}, isRetry = false): Promise<Response> {
  const headers: Record<string, string> = {}
  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`
  }

  let body: BodyInit | null = null
  if (options.files) {
    const formData = new FormData()
    for (const file of options.files) {
      formData.append('file', file)
    }
    if (options.body !== undefined) {
      // A Blob and not a plain string: appending a string gives the part no content type, Spring
      // reads it as `text/plain`, and `@RequestPart` then refuses to bind it to a record. Tagging
      // the part as JSON is what makes the two halves of the upload arrive as one typed request.
      formData.append('data', new Blob([JSON.stringify(options.body)], { type: 'application/json' }))
    }
    body = formData
  } else if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(options.body)
  }

  const response = await fetch(buildUrl(path, options.params), {
    method,
    headers,
    body,
    credentials: 'include',
  })

  if (response.status === 401 && !isRetry) {
    const newToken = await refreshSession()
    if (newToken) {
      return send(method, path, options, true)
    }
    setAccessToken(null)
    window.location.assign('/login')
    throw new ApiError(401, { title: 'Unauthorized', status: 401, detail: 'Session expired', errorCode: 'UNAUTHORIZED' })
  }

  if (!response.ok) {
    const problem = (await response.json()) as ProblemDetail
    throw new ApiError(response.status, problem)
  }

  return response
}

async function request<TRes>(method: string, path: string, options: RequestOptions = {}): Promise<TRes> {
  const response = await send(method, path, options)
  if (response.status === 204) {
    return undefined as TRes
  }
  return (await response.json()) as TRes
}

/**
 * What a download hands back: the bytes and the name to save them under.
 *
 * The name comes from the response, not from anything the caller knows, because it is the server
 * that holds the original filename - it is metadata on the file row and never part of a path (#80).
 */
export interface DownloadedFile {
  blob: Blob
  filename: string
}

/** Reads the filename out of a `Content-Disposition`, accepting both the RFC 5987 form and the plain one. */
function filenameFrom(header: string | null, fallback: string): string {
  if (!header) {
    return fallback
  }
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(header)
  if (encoded?.[1]) {
    return decodeURIComponent(encoded[1])
  }
  const plain = /filename="?([^";]+)"?/i.exec(header)
  return plain?.[1] ?? fallback
}

/**
 * Fetches a file's content.
 *
 * A plain `<a href>` cannot do this: the endpoint is authenticated with a bearer token, which the
 * browser never attaches on its own, so the bytes have to be fetched like any other call and handed
 * to the page as a blob.
 *
 * @param path     the content endpoint
 * @param fallback the name to save under if the response carries no `Content-Disposition`
 * @returns the bytes and the filename the server sent
 */
async function download(path: string, fallback: string): Promise<DownloadedFile> {
  const response = await send('GET', path)
  return {
    blob: await response.blob(),
    filename: filenameFrom(response.headers.get('Content-Disposition'), fallback),
  }
}

/**
 * The one HTTP client, typed (#104). It injects the bearer token, turns a `ProblemDetail` body into
 * a typed `ApiError`, and handles the 401 in a single place: refresh once, retry the call, and if
 * the refresh fails too, clear the session and go to /login.
 *
 * A feature never calls `fetch` directly - it builds its own module on top of this one, which is
 * what makes a backend contract change a compile error instead of a runtime surprise (#108).
 */
export const api = {
  get: <TRes>(path: string, params?: QueryParams) => request<TRes>('GET', path, { params }),
  getPage: <TItem>(path: string, params?: QueryParams) => request<PageResponse<TItem>>('GET', path, { params }),
  post: <TRes, TBody = unknown>(path: string, body?: TBody) => request<TRes>('POST', path, { body }),
  put: <TRes, TBody = unknown>(path: string, body: TBody) => request<TRes>('PUT', path, { body }),
  patch: <TRes, TBody = unknown>(path: string, body?: TBody) => request<TRes>('PATCH', path, { body }),
  delete: <TRes = void>(path: string) => request<TRes>('DELETE', path),
  upload: <TRes>(path: string, files: File[], body?: unknown) => request<TRes>('POST', path, { files, body }),
  /** Binary responses. Everything else here returns JSON; this one returns the bytes and a filename. */
  download: (path: string, fallbackFilename: string) => download(path, fallbackFilename),
}
