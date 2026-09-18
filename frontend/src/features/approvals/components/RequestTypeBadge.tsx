import { useTranslation } from 'react-i18next'

import { Badge } from '@/components/ui/badge'

import type { ApprovalRequestType } from '../types'

/**
 * What a request is asking for, as a chip.
 *
 * **Deliberately not a coloured state badge.** The kind of a request is not a state — it does not
 * move, and it carries no urgency — so colouring it would spend the one signal the tray has on
 * something that never changes. The state badge next to it is where colour belongs.
 *
 * The label is a `Record` lookup through the `admin` namespace, so a kind added to
 * `ApprovalRequestType` without a translation shows its key and is caught immediately rather than
 * rendering as an empty chip.
 *
 * @param props.type what the request asks for
 */
export function RequestTypeBadge({ type }: { type: ApprovalRequestType }) {
  const { t } = useTranslation('admin')

  return <Badge variant="outline">{t(`requests.types.${type}`)}</Badge>
}
