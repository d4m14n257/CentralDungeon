import { useTranslation } from 'react-i18next'

import { SubmitRequestSection } from '@/features/approvals'
import { HelpLink } from '@/features/help'

/**
 * `/help` — where somebody who read the explanations and did not find their answer asks an admin
 * directly (F3.2, #42).
 *
 * **It is not the old help route coming back.** #231 replaced `/help` with dialogs raised from the
 * screen that prompts the question, and that stands: the explanations still live there, next to the
 * thing being explained. What that decision left behind was a path reserved in so many words for the
 * support screen — "asking for assistance, reporting a bug" — with nothing behind it. The `General`
 * request is that backend, so the screen the reservation described can finally exist.
 *
 * It is deliberately short. A page of links to every help section would be the index #231 removed,
 * and an index nobody opens is worse than no index: it teaches people to look for answers in a place
 * that is not where the answers are. What it offers is the two things it is for — a way to ask, and
 * an explanation of what asking does.
 *
 * Transversal, like `/notifications` and `/my/schedule`: it belongs to whoever is reading it and to
 * none of the three contexts, which is why the header keeps the chip on wherever they came from
 * (#222).
 */
export function SupportPage() {
  const { t } = useTranslation('admin')

  return (
    <div className="space-y-4">
      <h1 className="font-serif text-2xl font-semibold">{t('requests.supportTitle')}</h1>
      <p className="text-fg-muted text-sm">{t('requests.supportDescription')}</p>

      <div className="border-border space-y-3 rounded-lg border p-4">
        <p className="text-sm font-medium">{t('requests.generalPitchTitle')}</p>
        <p className="text-fg-muted text-sm">{t('requests.generalPitchDescription')}</p>
        {/* The help arrives as a node, because a feature never imports another one (§3.1.5). */}
        <SubmitRequestSection
          type="General"
          help={
            <p className="text-fg-subtle text-xs">
              {t('requests.submitHelpHint')} <HelpLink section="basics.requests">{t('requests.submitHelpLink')}</HelpLink>
            </p>
          }
        />
      </div>

      {/* Where the rest of the explanations are, said once: they are dialogs on the screens that
          prompt them (#231), and somebody who came here looking for an index deserves to be told
          that rather than left to conclude the help is missing. */}
      <p className="text-fg-muted text-sm">
        {t('requests.supportWhereHelpLives')} <HelpLink section="basics.requests">{t('requests.supportHelpLink')}</HelpLink>
      </p>
    </div>
  )
}

export { SupportPage as Component }
