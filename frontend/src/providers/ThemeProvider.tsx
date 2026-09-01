import { ThemeProvider as NextThemesProvider } from 'next-themes'
import type { ReactNode } from 'react'

/**
 * Oscuro por defecto (#131) - la comunidad juega de noche; el claro se deriva de él.
 *
 * `enableSystem={false}` a propósito: "por defecto oscuro" es una decisión de diseño, no una
 * preferencia del sistema operativo. Quien quiera claro lo elige desde el UserMenu y queda
 * guardado. El atributo es `data-theme` porque es el selector que emite el design system
 * (`:root[data-theme="light"]` en el CSS transcrito, #118, #130).
 */
export function ThemeProvider({ children }: { children: ReactNode }) {
  return (
    <NextThemesProvider attribute="data-theme" defaultTheme="dark" enableSystem={false} disableTransitionOnChange>
      {children}
    </NextThemesProvider>
  )
}
