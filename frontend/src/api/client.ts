import { env } from '@/config/env'
import { authBlockedPath, paths } from '@/config/paths'
import { ApiError, type PageResponse, type ProblemDetail, type TokenResponse } from '@/types/api'

/**
 * The access token lives only in memory - never localStorage. AuthProvider
 * is the only caller of setAccessToken, right after OAuth2 login and after each refresh.
 */
let accessToken: string | null = null

/**
 * Stores the access token every later request is signed with, or clears it on logout.
 *
 * The token lives **in memory only** - never `localStorage`, never `sessionStorage`. The app
 * renders rich text written by other people, which is the most direct XSS surface it has, and
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

const ACCOUNT_BLOCKED = 'ACCOUNT_BLOCKED'

/** Whether a 401 says the account is blocked, as opposed to the session merely being gone. Reads a clone, so the body stays readable. */
async function isAccountBlocked(response: Response): Promise<boolean> {
  const problem = (await response
    .clone()
    .json()
    .catch(() => null)) as Partial<ProblemDetail> | null
  return problem?.errorCode === ACCOUNT_BLOCKED
}

/**
 * Ends the session for a blocked account and sends the person to the screen that says so, instead
 * of to a login that would tell them nothing. Skips the navigation when they are already there, so
 * the boot refresh of that very screen cannot reload it in a loop.
 *
 * @returns the error to throw, so the call that hit the block does not go on as if nothing happened
 */
function leaveForBlockedScreen(): ApiError {
  setAccessToken(null)
  if (window.location.pathname !== paths.authCallback) {
    window.location.assign(authBlockedPath())
  }
  return new ApiError(401, { title: 'Unauthorized', status: 401, detail: 'Account blocked', errorCode: ACCOUNT_BLOCKED })
}

/** The CSRF header the two cookie-authenticated endpoints, refresh and logout, require. */
function csrfHeaders(): Record<string, string> {
  const csrfToken = readCookie('XSRF-TOKEN')
  return csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}
}

/** The renewal currently on the wire in this tab, shared by everybody who asks for one while it lasts. */
let refreshInFlight: Promise<string | null> | null = null

/** Name of the lock that lets only one tab at a time renew the session. */
const REFRESH_LOCK_NAME = 'central-dungeon-session-refresh'

/**
 * Renews the access token from the httpOnly refresh cookie. Exported so AuthProvider can call it once
 * on boot to recover a session, without a chicken-and-egg 401 first.
 *
 * **One renewal at a time, in this tab and across tabs.** The server spends the refresh token on every
 * renewal and reads a second use of it as theft, ending the session. Inside a tab, every caller that
 * asks while a renewal is on the wire - several calls hitting a 401 together, or the boot refresh
 * running twice - gets that same renewal's result instead of sending the same cookie again.
 *
 * Across tabs, a shared `navigator.locks` lock serializes the renewals: two tabs that renew together
 * would present the same cookie, and the server would take the second for a stolen copy and end the
 * session in every tab. The tab that waits for the lock **still sends its own refresh once it gets
 * it**, and that is correct: the refresh cookie is httpOnly and lives in the one cookie jar all the
 * tabs of the browser share, so the winner's response already replaced it, and the waiter's request -
 * which reads the jar when it is sent, not when it queued - carries the new cookie. The CSRF header is
 * read inside the lock for the same reason. Skipping the call instead would leave the waiter without
 * an access token of its own.
 *
 * Where `navigator.locks` does not exist (old browsers, jsdom) only the per-tab sharing applies.
 *
 * @returns the new access token, or null if there is no session to recover
 * @throws ApiError with `ACCOUNT_BLOCKED` if the account is blocked; the person has already been sent to the blocked screen
 * @throws ApiError with `RATE_LIMITED` if renewals came too fast; the session is kept and nobody is sent to /login
 */
export function refreshSession(): Promise<string | null> {
  refreshInFlight ??= renewAcrossTabs().finally(() => {
    refreshInFlight = null
  })
  return refreshInFlight
}

/** Runs the renewal while holding the cross-tab lock, or straight away if the browser has none. */
async function renewAcrossTabs(): Promise<string | null> {
  // Typed as always present by the DOM lib, but it is missing in old browsers and in jsdom.
  const locks = navigator.locks as LockManager | undefined
  return locks ? await locks.request(REFRESH_LOCK_NAME, renewAccessToken) : renewAccessToken()
}

async function renewAccessToken(): Promise<string | null> {
  const response = await fetch(buildUrl('/api/v1/auth/refresh'), {
    method: 'POST',
    credentials: 'include',
    headers: csrfHeaders(),
  })
  if (!response.ok) {
    if (response.status === 401 && (await isAccountBlocked(response))) {
      throw leaveForBlockedScreen()
    }
    // Too many renewals is not a lost session: the cookie was never spent, so the call fails and the session stays.
    if (response.status === 429) {
      throw new ApiError(429, (await response.json()) as ProblemDetail)
    }
    return null
  }
  const data = (await response.json()) as TokenResponse
  setAccessToken(data.accessToken)
  return data.accessToken
}

/**
 * Ends the session on the server: the refresh cookie is cleared and every token descended from the
 * same login is revoked. Like the refresh, it is authenticated by the cookie, so it sends the CSRF header.
 *
 * @throws ApiError if the server refuses the call
 */
export async function endSession(): Promise<void> {
  const response = await fetch(buildUrl('/api/v1/auth/logout'), {
    method: 'POST',
    credentials: 'include',
    headers: csrfHeaders(),
  })
  if (!response.ok) {
    throw new ApiError(response.status, (await response.json()) as ProblemDetail)
  }
}

/**
 * Sends the call and settles the 401 - refresh once, retry, and give up to /login if that fails too. A 401 that says the account is blocked skips all that and goes to the blocked screen.
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

  if (response.status === 401 && (await isAccountBlocked(response))) {
    throw leaveForBlockedScreen()
  }

  if (response.status === 401 && !isRetry) {
    const newToken = await refreshSession()
    if (newToken) {
      return send(method, path, options, true)
    }
    setAccessToken(null)
    window.location.assign(paths.login)
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
 * A response body together with the status that carried it.
 *
 * Almost every call in this app only wants the body — the status either was 2xx or already became an
 * `ApiError`. This exists for the handful where a 200 and a 201 **mean different things**, which is
 * the uploads, where a file that was recognised rather than written answers 200, and that is the
 * only signal telling the screen to say so.
 *
 * @template TRes the response body's type
 */
interface WithStatus<TRes> {
  data: TRes
  status: number
}

/** As {@link request}, but keeping the status for the callers whose meaning depends on it. */
async function requestWithStatus<TRes>(method: string, path: string, options: RequestOptions = {}): Promise<WithStatus<TRes>> {
  const response = await send(method, path, options)
  const data = response.status === 204 ? (undefined as TRes) : ((await response.json()) as TRes)
  return { data, status: response.status }
}

/**
 * What a download hands back: the bytes and the name to save them under.
 *
 * The name comes from the response, not from anything the caller knows, because it is the server
 * that holds the original filename - it is metadata on the file row and never part of a path.
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
 * The one HTTP client, typed. It injects the bearer token, turns a `ProblemDetail` body into
 * a typed `ApiError`, and handles the 401 in a single place: refresh once, retry the call, and if
 * the refresh fails too, clear the session and go to /login.
 *
 * A feature never calls `fetch` directly - it builds its own module on top of this one, which is
 * what makes a backend contract change a compile error instead of a runtime surprise.
 */
export const api = {
  get: <TRes>(path: string, params?: QueryParams) => request<TRes>('GET', path, { params }),
  getPage: <TItem>(path: string, params?: QueryParams) => request<PageResponse<TItem>>('GET', path, { params }),
  post: <TRes, TBody = unknown>(path: string, body?: TBody) => request<TRes>('POST', path, { body }),
  put: <TRes, TBody = unknown>(path: string, body: TBody) => request<TRes>('PUT', path, { body }),
  patch: <TRes, TBody = unknown>(path: string, body?: TBody) => request<TRes>('PATCH', path, { body }),
  delete: <TRes = void>(path: string) => request<TRes>('DELETE', path),
  upload: <TRes>(path: string, files: File[], body?: unknown) => request<TRes>('POST', path, { files, body }),
  /** An upload whose caller needs the status: 201 wrote something, 200 recognised it. */
  uploadWithStatus: <TRes>(path: string, files: File[], body?: unknown) => requestWithStatus<TRes>('POST', path, { files, body }),
  /** Binary responses. Everything else here returns JSON; this one returns the bytes and a filename. */
  download: (path: string, fallbackFilename: string) => download(path, fallbackFilename),
}
