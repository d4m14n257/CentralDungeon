import type { NodeProps } from '@xyflow/react'
import { useTranslation } from 'react-i18next'

import { GraphNode } from '@/components/graph/GraphNode'

import { useCatalogGraph } from '../graph/catalogGraphContext'
import type { CatalogFlowNode } from '../graph/toFlow'
import { CATALOG_STATUS_TONES } from './catalogStatusTones'
import { CatalogValueActions } from './CatalogValueActions'

/**
 * One catalog value on the canvas (#275): the base `GraphNode` of skill `diseno` §5 with this
 * feature's words and actions in it.
 *
 * Which handles are live follows the canvas's grammar (`connectionIntent`): anything with
 * somewhere to go can start a connection, and only an accepted head can receive one - depth is
 * always 1 (#59), so nothing hangs from an alias.
 *
 * @param props.data     the value and its place in the star
 * @param props.selected whether the canvas has it selected
 */
export function CatalogGraphNode({ data, selected }: NodeProps<CatalogFlowNode>) {
  const { t } = useTranslation('catalogs')
  const { kind, onBecameGroup } = useCatalogGraph()
  const { value, role } = data

  const isAcceptedHead = role === 'head' && value.status === 'Accepted'
  const meta =
    role === 'floating'
      ? t('admin.floatingHint')
      : role === 'head'
        ? t('admin.aliasCount', { count: value.aliasCount })
        : t('admin.usesCount', { count: value.uses })

  return (
    <GraphNode
      label={value.name}
      role={role}
      tone={CATALOG_STATUS_TONES[value.status]}
      statusLabel={t(`status.${value.status}`)}
      selected={selected}
      connectableFrom={role !== 'head' || value.status === 'Accepted'}
      connectableTo={isAcceptedHead}
      meta={meta}
      headLabel={t('admin.headLabel')}
      actions={<CatalogValueActions kind={kind} value={value} onBecameGroup={onBecameGroup} />}
    />
  )
}
