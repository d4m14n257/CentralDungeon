import { ThemeProvider as NextThemesProvider } from 'next-themes'
import type { ReactNode } from 'react'

/** Dark by default (decisiones.md #131) - the community plays at night; light derives from it. */
export function ThemeProvider({ children }: { children: ReactNode }) {
  return (
    <NextThemesProvider attribute="data-theme" defaultTheme="dark">
      {children}
    </NextThemesProvider>
  )
}
