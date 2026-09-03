import { createContext, use, useEffect, useState, type ReactNode } from 'react'

import { refreshSession, setAccessToken } from '@/api/client'

interface AuthContextValue {
  isBootstrapping: boolean
  isAuthenticated: boolean
  signOut: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

/**
 * On mount, tries the refresh cookie once to recover a session (e.g. the user already logged in
 * and just reloaded the tab) - no chicken-and-egg 401 needed. Client-level 401 handling covers
 * the rest of the app's lifetime; this is only the initial bootstrap.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [isBootstrapping, setIsBootstrapping] = useState(true)
  const [isAuthenticated, setIsAuthenticated] = useState(false)

  useEffect(() => {
    let cancelled = false
    refreshSession()
      .then((token) => {
        if (cancelled) return
        setIsAuthenticated(token !== null)
      })
      .catch(() => {
        if (!cancelled) setIsAuthenticated(false)
      })
      .finally(() => {
        if (!cancelled) setIsBootstrapping(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  function signOut() {
    setAccessToken(null)
    setIsAuthenticated(false)
  }

  return <AuthContext value={{ isBootstrapping, isAuthenticated, signOut }}>{children}</AuthContext>
}

/**
 * The session: who is signed in, whether it is still loading, and how to end it.
 *
 * What it exposes is **for deciding what to render, never what to allow** (#103). The backend
 * authorizes endpoint by endpoint, and no component decides a permission by reading this.
 *
 * @returns the session context
 * @throws if called outside `AuthProvider`
 */
export function useAuth(): AuthContextValue {
  const context = use(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return context
}
