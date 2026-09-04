import { ThemeProvider as NextThemesProvider } from 'next-themes'
import type { ReactNode } from 'react'

/**
 * Dark by default (#131) - the community plays at night; the light theme is derived from it.
 *
 * `enableSystem={false}` on purpose: "dark by default" is a design decision, not an operating
 * system preference. Somebody who wants light picks it from the UserMenu and it is remembered. The
 * attribute is `data-theme` because that is the selector the design system emits
 * (`:root[data-theme="light"]` in the transcribed CSS, #118, #130).
 */
export function ThemeProvider({ children }: { children: ReactNode }) {
  return (
    <NextThemesProvider attribute="data-theme" defaultTheme="dark" enableSystem={false} disableTransitionOnChange>
      {children}
    </NextThemesProvider>
  )
}
