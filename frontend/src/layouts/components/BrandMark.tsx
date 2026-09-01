import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/utils'

/**
 * El wordmark en dos tonos. El acento aparece como relleno sólido o como esta segunda mitad,
 * nunca como fondo suave: eso está reservado a los estados (frontend-diseno.md 3).
 *
 * No lleva enlace propio — en el header lo envuelve un Link a la home, y en /login no hay
 * adónde ir.
 */
export function BrandMark({ className }: { className?: string }) {
  const { t } = useTranslation('common')

  return (
    <span className={cn('font-serif text-base font-bold', className)}>
      <span className="text-fg">{t('nav.brandFirst')}</span>
      <span className="text-brand-fg">{t('nav.brandSecond')}</span>
    </span>
  )
}
