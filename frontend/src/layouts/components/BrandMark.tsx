import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/utils'

/**
 * The wordmark in two tones. The accent appears as a solid fill or as this second half, never as a
 * soft background: that is reserved for the states (frontend-diseno.md 3).
 *
 * It carries no link of its own — in the header a Link to the home wraps it, and on /login there is
 * nowhere to go.
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
