import { Languages } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { LANGUAGES } from '@/config/language'
import { useLanguage } from '@/hooks/useLanguage'
import { cn } from '@/lib/utils'

/**
 * A compact language switch that shows every option at once (#198).
 *
 * **All the options are visible, not behind a menu.** This exists for the screens with no
 * `UserMenu` — `/login` above all — where somebody who cannot read the language on screen has to be
 * able to tell at a glance that it can be changed. A dropdown would hide exactly the thing they are
 * looking for behind a control they have to guess at first.
 *
 * Each language names itself: somebody hunting for their own does not necessarily read the one
 * currently active. The globe carries the meaning for anybody who reads neither.
 *
 * @param props.className extra classes for placing it in its container
 */
export function LanguageSwitch({ className }: { className?: string }) {
  const { t } = useTranslation('common')
  const { language, setLanguage } = useLanguage()

  return (
    <div role="group" aria-label={t('language.label')} className={cn('flex items-center justify-center gap-1', className)}>
      <Languages aria-hidden="true" className="text-fg-subtle size-3.5" />
      {LANGUAGES.map((code) => {
        const active = code === language
        return (
          <button
            key={code}
            type="button"
            // aria-pressed and not aria-current: this is a toggle for a setting, not a location.
            aria-pressed={active}
            onClick={() => setLanguage(code)}
            className={cn(
              'rounded-md px-2 py-1 text-xs transition-colors',
              active ? 'bg-raised text-fg font-medium' : 'text-fg-muted hover:text-fg',
            )}
          >
            {t(`language.${code}`)}
          </button>
        )
      })}
    </div>
  )
}
