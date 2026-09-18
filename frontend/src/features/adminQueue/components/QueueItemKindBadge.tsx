import { useTranslation } from 'react-i18next'

import { Badge } from '@/components/ui/badge'

import type { AdminQueueItemKind } from '../types'

/**
 * What kind of work a tray row is, as a chip.
 *
 * **Deliberately not a coloured state badge**, the same reading as `RequestTypeBadge`: the kind of an
 * item is not a state — it does not move and it carries no urgency — so colouring it would spend the
 * one signal the row has on something that never changes. What is coloured here is the reservation,
 * because that is the thing that changes under the reader's feet.
 *
 * The label is a `Record` lookup through the `admin` namespace, so a kind added to
 * `AdminQueueItemKind` without a translation shows its key and is caught immediately rather than
 * rendering as an empty chip — which is what F5 will do when it adds the two sources F3.3 left out.
 *
 * @param props.kind what sort of work the row is
 */
export function QueueItemKindBadge({ kind }: { kind: AdminQueueItemKind }) {
  const { t } = useTranslation('admin')

  return <Badge variant="outline">{t(`queue.kinds.${kind}`)}</Badge>
}
