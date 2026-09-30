import { CircleHelp } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'

import type { HelpSectionBodyProps, HelpSectionId } from '../sections/registry'
import { HelpDialog } from './HelpDialog'

/** What a help button opens, and how big it is. */
export interface HelpButtonProps extends HelpSectionBodyProps {
  /** Which piece of help to open. */
  section: HelpSectionId
  /**
   * `default` for a screen's header, `sm` for a section's (#280). Both say «Cómo funciona»: the
   * place tells the reader what "this" is.
   */
  size?: 'default' | 'sm'
}

/**
 * «Cómo funciona» for a whole screen or a whole section, always in the same place: the right end of
 * the header, before the main action (#280).
 *
 * **Not the same thing as `HelpLink`.** A `HelpLink` sits inside a sentence next to one control —
 * "para pausar la mesa… cómo pausar" — and belongs where the thing it explains is. This one answers
 * "how does this screen work", and until it had a fixed place every screen put it somewhere else:
 * across from the title, under it, at the bottom of the page, or nowhere.
 *
 * A ghost button with the icon, so it reads as an action and not as body text, but never competes
 * with the one violet button next to it.
 *
 * @param props.section which piece of help to open
 * @param props.size    `default` in a screen's header, `sm` in a section's
 */
export function HelpButton({ section, size = 'default', ...context }: HelpButtonProps) {
  const { t } = useTranslation('help')
  const [open, setOpen] = useState(false)

  return (
    <>
      <Button type="button" variant="ghost" size={size} onClick={() => setOpen(true)}>
        <CircleHelp className="size-4" aria-hidden="true" />
        {t('howItWorks')}
      </Button>
      <HelpDialog section={section} open={open} onOpenChange={setOpen} {...context} />
    </>
  )
}
