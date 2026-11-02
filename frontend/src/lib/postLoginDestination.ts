/**
 * Where a visitor without a session was headed, kept across the login so they land there afterwards
 * instead of on their home.
 *
 * The OAuth round-trip is a full page load, so router state cannot carry it: it lives in
 * `sessionStorage`, which survives the redirect and dies with the tab. It holds a path, never a token.
 */
const KEY = 'postLoginDestination'

/**
 * Whether a value is a path inside this app, and therefore safe to navigate to. Anything else - an
 * absolute URL, a protocol-relative `//host`, a backslash variant a browser reads as `/` - is how an
 * open redirect is built, so it is refused.
 *
 * @param value what was stored or is about to be
 * @returns true when it is a same-origin path
 */
export function isInAppPath(value: string): boolean {
  if (!value.startsWith('/') || value.startsWith('//')) return false
  try {
    return new URL(value, window.location.origin).origin === window.location.origin
  } catch {
    return false
  }
}

/**
 * Remembers the path a visitor without a session tried to open, so the login can send them back to it.
 * A value that is not an in-app path is ignored.
 *
 * @param path the path, with its query and hash
 */
export function rememberDestination(path: string): void {
  if (!isInAppPath(path)) return
  try {
    sessionStorage.setItem(KEY, path)
  } catch {
    // Storage blocked: the visitor simply lands on their home after logging in.
  }
}

/**
 * Reads the remembered path without consuming it, ignoring anything that is not an in-app path.
 *
 * @returns the path, or null when there is none or it is not safe
 */
export function peekDestination(): string | null {
  try {
    const value = sessionStorage.getItem(KEY)
    return value !== null && isInAppPath(value) ? value : null
  } catch {
    return null
  }
}

/** Forgets the remembered path. Called once it has been honoured, so it cannot be honoured twice. */
export function clearDestination(): void {
  try {
    sessionStorage.removeItem(KEY)
  } catch {
    // Nothing to clear if storage is unavailable.
  }
}
