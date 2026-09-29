import '@xyflow/react/dist/style.css'

import { useTranslation } from 'react-i18next'
import { Background, Controls, ReactFlow, type Edge, type Node, type ReactFlowProps } from '@xyflow/react'

import { GraphStraightEdge } from './GraphStraightEdge'

/**
 * The edge types every canvas gets. `straight-center` is the pattern's spoke (`GraphStraightEdge`);
 * module level, so React Flow never sees a new map and never warns about it.
 */
const EDGE_TYPES = { 'straight-center': GraphStraightEdge }

/** What the canvas takes: everything React Flow takes, plus the name a screen reader announces. */
export interface GraphCanvasProps<N extends Node, E extends Edge> extends Omit<ReactFlowProps<N, E>, 'aria-label'> {
  /** What the canvas shows, already passed through `t()` - its accessible name. */
  label: string
}

/**
 * The node canvas of skill `diseno` §5 (#275): React Flow with the project's frame, background,
 * zoom controls and theme, so a screen only brings its nodes, its edges and what a connection means.
 *
 * **It is the only file that imports React Flow's stylesheet**, and it does so from here rather
 * than from `globals.css` because that file is rewritten whole every time the theme is transcribed
 * (#118, #130). The library's colours are its `--xy-*` variables, and `.graph-canvas` points every
 * one of them at a token of the `@theme` - so the canvas follows the light and dark themes with the
 * rest of the app and carries no loose value (regla dura 18).
 *
 * Screens load it through `lazy()` on their route, so React Flow never reaches the bundle of an
 * account that never opens a canvas.
 *
 * The controls' accessible names come from `t()` too, through React Flow's `ariaLabelConfig`: the
 * library ships them in English, and a Spanish screen reader reading "Zoom In" is the half-translated
 * interface #198 forbids.
 *
 * @param props.label  the canvas's accessible name
 * @param props.nodes  the nodes, as React Flow takes them
 * @param props.edges  the edges, as React Flow takes them
 * @param props.rest   anything else React Flow accepts: node types, connection callbacks, …
 */
export function GraphCanvas<N extends Node, E extends Edge>({ label, ...props }: GraphCanvasProps<N, E>) {
  const { t } = useTranslation('common')

  return (
    <div className="graph-canvas">
      <ReactFlow<N, E>
        aria-label={label}
        edgeTypes={EDGE_TYPES}
        fitView
        fitViewOptions={{ padding: 0.2 }}
        minZoom={0.2}
        ariaLabelConfig={{
          'controls.ariaLabel': t('graph.controls'),
          'controls.zoomIn.ariaLabel': t('graph.zoomIn'),
          'controls.zoomOut.ariaLabel': t('graph.zoomOut'),
          'controls.fitView.ariaLabel': t('graph.fitView'),
          'controls.interactive.ariaLabel': t('graph.interactive'),
          'handle.ariaLabel': t('graph.handle'),
          'node.a11yDescription.default': t('graph.nodeDescription'),
          'node.a11yDescription.keyboardDisabled': t('graph.nodeDescriptionKeyboardDisabled'),
          'edge.a11yDescription.default': t('graph.edgeDescription'),
        }}
        {...props}
      >
        <Background gap={24} />
        <Controls showInteractive={false} />
      </ReactFlow>
    </div>
  )
}
