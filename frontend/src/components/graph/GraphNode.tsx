import type { ReactNode } from 'react'
import { Handle, Position } from '@xyflow/react'
import { Crown } from 'lucide-react'

import { StatusBadge, type StatusTone } from '@/components/StatusBadge'
import { cn } from '@/lib/utils'

/**
 * The three places a node can hold in a node canvas (skill `diseno` §5, #275):
 *
 * - `head` - the centre of a star, the one the others hang from
 * - `member` - hanging from a head
 * - `floating` - connected to nothing yet, waiting for somebody to connect it
 */
export type GraphNodeRole = 'head' | 'member' | 'floating'

/** What a canvas node draws. */
export interface GraphNodeProps {
  /** The node's name, already passed through `t()` if it is not data. */
  label: string
  /** Where the node stands in the graph; it decides the frame, not the colour. */
  role: GraphNodeRole
  /** The state the node is in, as a `StatusBadge` tone - the colour is never the only carrier (#261). */
  tone: StatusTone
  /** The state's name, written next to its dot. */
  statusLabel: string
  /** Whether the node is currently selected on the canvas. */
  selected?: boolean
  /** Whether a connection can start from this node. */
  connectableFrom?: boolean
  /** Whether a connection can end on this node. */
  connectableTo?: boolean
  /** The node's actions menu - the keyboard's way to do what dragging does (#275). */
  actions?: ReactNode
  /** A secondary line under the name: a count, a hint. */
  meta?: ReactNode
  /**
   * What the head of a star is called in this domain - "Principal", "Main" - already passed through
   * `t()`. Shown with a crown above the name of a `head` node, and ignored for the other roles.
   */
  headLabel?: string
}

/**
 * The base node of the node canvas pattern (skill `diseno` §5, #275): a name, its state as a
 * `StatusBadge`, an optional line under it and an actions menu, with the two handles a connection
 * starts and ends on.
 *
 * **Generic on purpose.** A feature brings its own node types and renders this inside them with
 * its own labels and actions, so a second canvas never has to import the first one's domain
 * (regla dura 16).
 *
 * Connections go from the right handle of one node to the left handle of another: always the same
 * direction, so the gesture reads as "this goes into that".
 *
 * @param props.label           the node's name
 * @param props.role            head, member or floating
 * @param props.tone            the state's tone
 * @param props.statusLabel     the state's name
 * @param props.selected        whether the canvas has it selected
 * @param props.connectableFrom whether a connection can start here
 * @param props.connectableTo   whether a connection can end here
 * @param props.actions         the node's actions menu
 * @param props.meta            a secondary line under the name
 * @param props.headLabel       what a head is called, shown with a crown on `head` nodes
 */
export function GraphNode({
  label,
  role,
  tone,
  statusLabel,
  selected = false,
  connectableFrom = true,
  connectableTo = true,
  actions,
  meta,
  headLabel,
}: GraphNodeProps) {
  return (
    <div className={cn('graph-node', `graph-node-${role}`, selected && 'graph-node-selected')}>
      <Handle type="target" position={Position.Left} isConnectable={connectableTo} className="graph-handle" />
      <div className="min-w-0 flex-1 space-y-1">
        {/* The head is said, not only framed: a crown and its name, so which node the others hang
            from reads at a glance and to a screen reader, never by the border colour alone (#261). */}
        {role === 'head' && headLabel && (
          <p className="graph-node-head-marker">
            <Crown className="size-3.5" aria-hidden="true" />
            {headLabel}
          </p>
        )}
        <p className={cn('truncate text-sm', role === 'head' ? 'font-semibold' : 'font-medium')}>{label}</p>
        <StatusBadge tone={tone} label={statusLabel} />
        {meta && <p className="text-fg-subtle text-xs">{meta}</p>}
      </div>
      {/* nodrag/nopan: clicking the menu must open it, not start dragging the node. */}
      {actions && <div className="nodrag nopan shrink-0">{actions}</div>}
      <Handle type="source" position={Position.Right} isConnectable={connectableFrom} className="graph-handle" />
    </div>
  )
}
