import { useEffect, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { useEdgesState, useNodesState, type Connection, type Edge, type IsValidConnection, type OnBeforeDelete } from '@xyflow/react'

import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { ForbiddenState } from '@/components/ForbiddenState'
import { GraphCanvas } from '@/components/graph/GraphCanvas'
import { Skeleton } from '@/components/ui/skeleton'
import { useConfirm } from '@/hooks/useConfirm'
import { ApiError } from '@/types/api'

import { useAcceptCatalogValue } from '../api/useAcceptCatalogValue'
import { useAdminCatalog } from '../api/useAdminCatalog'
import { useCatalogGroups } from '../api/useCatalogGroups'
import { useMergeCatalogGroups } from '../api/useMergeCatalogGroups'
import { useReassignCatalogValue } from '../api/useReassignCatalogValue'
import { useSplitCatalogGroup } from '../api/useSplitCatalogGroup'
import { CatalogGraphContext } from '../graph/catalogGraphContext'
import { connectionIntent } from '../graph/connectionIntent'
import { CATALOG_NODE_TYPE, toFlow, type CatalogFlowEdge, type CatalogFlowNode } from '../graph/toFlow'
import type { AdminCatalogValue, CatalogKind } from '../types'
import { CatalogGraphNode } from './CatalogGraphNode'
import { CatalogStatusBadge } from './CatalogStatusBadge'
import { CatalogValueActions } from './CatalogValueActions'

/** The one node type the canvas registers. Module level, so React Flow never sees a new map. */
const NODE_TYPES = { [CATALOG_NODE_TYPE]: CatalogGraphNode }

/** How many floating proposals the canvas brings at most: a review queue, not the whole catalog. */
const FLOATING_LIMIT = 50

/** What the canvas needs. */
export interface CatalogGraphProps {
  /** Which catalog it draws. */
  kind: CatalogKind
  /** Any member of each group to draw. The first one is the group the admin opened. */
  groupIds: string[]
  /** Called with a value that has just become a group of its own, so the screen adds it to `groupIds`. */
  onBecameGroup: (id: string) => void
}

/**
 * The catalog canvas (#275): the groups an admin has open, drawn as stars, and the proposals
 * nobody has classified yet floating to their left until somebody connects them.
 *
 * **Connecting is classifying.** Dropping one value on a group's head does what the canvas grammar
 * says it means (`connectionIntent`): a proposal is accepted into the group, an alias moves to it
 * (#276), a whole group merges into it - that last one confirmed first, because it moves every
 * member. Deleting an edge splits the alias out. Nothing is drawn locally: the mutation runs, the
 * catalog branch is invalidated, and the canvas redraws from what the server says, so it never shows
 * a state the server refused.
 *
 * **Below `md` there is no canvas** (skill `diseno` §5.b): dragging a connection with a thumb is not
 * something anyone finishes. The same groups become a list with the same per-value menu, which is
 * also the keyboard's way through on a wide screen - every gesture has its entry there.
 *
 * @param props.kind          which catalog
 * @param props.groupIds      the groups to draw
 * @param props.onBecameGroup called when a value becomes a group of its own
 */
export function CatalogGraph({ kind, groupIds, onBecameGroup }: CatalogGraphProps) {
  const { t } = useTranslation('catalogs')
  const confirm = useConfirm()
  const accept = useAcceptCatalogValue(kind)
  const reassign = useReassignCatalogValue(kind)
  const merge = useMergeCatalogGroups(kind)
  const split = useSplitCatalogGroup(kind)

  const { groups, isPending: groupsPending, error: groupsError, refetch: refetchGroups } = useCatalogGroups(kind, groupIds)
  const pendingQuery = useAdminCatalog(kind, '', ['Created'], 0, FLOATING_LIMIT, { groupsOnly: true })

  const pending = useMemo(() => pendingQuery.data?.content ?? [], [pendingQuery.data])
  const flow = useMemo(() => toFlow(groups, pending), [groups, pending])
  const values = useMemo(() => new Map(flow.nodes.map((node) => [node.id, node.data.value])), [flow])

  // Controlled so the admin can drag nodes around and select an edge to delete it; reset whenever
  // the server's answer changes, which is the only thing that moves a value between groups.
  const [nodes, setNodes, onNodesChange] = useNodesState<CatalogFlowNode>(flow.nodes)
  const [edges, setEdges, onEdgesChange] = useEdgesState<CatalogFlowEdge>(flow.edges)
  useEffect(() => {
    setNodes(flow.nodes)
    setEdges(flow.edges)
  }, [flow, setNodes, setEdges])

  const isValidConnection: IsValidConnection<CatalogFlowEdge> = (connection) => {
    const source = values.get(connection.source)
    const target = values.get(connection.target)
    return source !== undefined && target !== undefined && connectionIntent(source, target) !== null
  }

  async function handleConnect(connection: Connection) {
    const source = values.get(connection.source)
    const target = values.get(connection.target)
    if (!source || !target) return

    switch (connectionIntent(source, target)) {
      case 'accept':
        accept.mutate(
          { id: source.id, canonicalId: target.id },
          { onSuccess: () => toast.success(t('admin.acceptIntoSuccess', { name: source.name, target: target.name })) },
        )
        return
      case 'reassign':
        reassign.mutate(
          { id: source.id, canonicalId: target.id },
          { onSuccess: () => toast.success(t('admin.reassignSuccess', { name: source.name, target: target.name })) },
        )
        return
      case 'merge': {
        const confirmed = await confirm({
          title: t('admin.mergeConfirmTitle', { source: source.name, target: target.name }),
          description: t('admin.mergeHint', { source: source.name }),
          confirmLabel: t('admin.merge'),
        })
        if (!confirmed) return
        merge.mutate(
          { sourceCanonicalId: source.id, targetCanonicalId: target.id },
          { onSuccess: () => toast.success(t('admin.mergeSuccess', { source: source.name, target: target.name })) },
        )
        return
      }
      case null:
        return
    }
  }

  // Deleting an edge is splitting its alias out. Nodes are never deleted from here; and the edge is
  // not removed locally either - the answer is always false, and the redraw after the split does it.
  const handleBeforeDelete: OnBeforeDelete<CatalogFlowNode, Edge> = async ({ nodes: deletedNodes, edges: deleted }) => {
    // A selected node drags its edges into the deletion; that is not a request to split anything.
    if (deletedNodes.length > 0 || deleted.length !== 1) return false
    const [edge] = deleted
    const member = edge ? values.get(edge.source) : undefined
    if (!member) return false
    const confirmed = await confirm({
      title: t('admin.splitConfirmTitle', { name: member.name }),
      description: t('admin.splitConfirmDescription'),
    })
    if (confirmed) {
      split.mutate(member.id, {
        onSuccess: () => {
          toast.success(t('admin.splitSuccess', { name: member.name }))
          onBecameGroup(member.id)
        },
      })
    }
    return false
  }

  const failed = groupsError ?? (pendingQuery.isLoadingError ? pendingQuery.error : null)
  if (failed instanceof ApiError && failed.status === 403) {
    return <ForbiddenState />
  }
  if (failed) {
    return (
      <ErrorState
        onRetry={() => {
          refetchGroups()
          void pendingQuery.refetch()
        }}
      />
    )
  }
  if (groupsPending || pendingQuery.isPending) {
    return <Skeleton className="h-160 w-full" />
  }
  if (flow.nodes.length === 0) {
    return <EmptyState title={t('admin.graphEmptyTitle')} description={t('admin.graphEmptyDescription')} />
  }

  return (
    <CatalogGraphContext value={{ kind, onBecameGroup }}>
      <div className="hidden md:block">
        <GraphCanvas<CatalogFlowNode, CatalogFlowEdge>
          label={t('admin.graphLabel', { kind: t(`kind.${kind}.label`) })}
          nodes={nodes}
          edges={edges}
          nodeTypes={NODE_TYPES}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          isValidConnection={isValidConnection}
          onConnect={(connection) => void handleConnect(connection)}
          onBeforeDelete={handleBeforeDelete}
        />
      </div>

      <div className="space-y-6 md:hidden">
        {groups.map((group) => (
          <section key={group[0]?.id} className="space-y-2">
            <h2 className="section-label">{t('admin.groupOf', { name: group[0]?.name ?? '' })}</h2>
            <ul className="list-divided">
              {group.map((member) => (
                <GraphListRow key={member.id} kind={kind} value={member} onBecameGroup={onBecameGroup} />
              ))}
            </ul>
          </section>
        ))}
        {pending.length > 0 && (
          <section className="space-y-2">
            <h2 className="section-label">{t('admin.floatingTitle')}</h2>
            <ul className="list-divided">
              {pending.map((proposal) => (
                <GraphListRow key={proposal.id} kind={kind} value={proposal} onBecameGroup={onBecameGroup} />
              ))}
            </ul>
          </section>
        )}
      </div>
    </CatalogGraphContext>
  )
}

/**
 * One value of the list the canvas becomes below `md`: its name, its state and the same menu a
 * node carries.
 *
 * @param props.kind          which catalog
 * @param props.value         the value
 * @param props.onBecameGroup called when the value becomes a group of its own
 */
function GraphListRow({
  kind,
  value,
  onBecameGroup,
}: {
  kind: CatalogKind
  value: AdminCatalogValue
  onBecameGroup: (id: string) => void
}) {
  return (
    <li className="flex items-center gap-3 px-3 py-2">
      <span className={value.canonicalId === null ? 'min-w-0 flex-1 truncate font-semibold' : 'min-w-0 flex-1 truncate'}>{value.name}</span>
      <CatalogStatusBadge status={value.status} />
      <div className="row-actions">
        <CatalogValueActions kind={kind} value={value} onBecameGroup={onBecameGroup} />
      </div>
    </li>
  )
}
