import { render, screen } from '@testing-library/react'
import { ReactFlowProvider } from '@xyflow/react'
import { describe, expect, it } from 'vitest'

import { GraphNode, type GraphNodeRole } from './GraphNode'

function renderNode(role: GraphNodeRole) {
  return render(
    <ReactFlowProvider>
      <GraphNode label="D&D 5e" role={role} tone="open" statusLabel="Aceptado" headLabel="Principal" />
    </ReactFlowProvider>,
  )
}

describe('GraphNode', () => {
  it('says which node is the head, in words and not only with its frame', () => {
    renderNode('head')

    expect(screen.getByText('Principal')).toBeInTheDocument()
  })

  it.each<GraphNodeRole>(['member', 'floating'])('never marks a %s as the head', (role) => {
    renderNode(role)

    expect(screen.queryByText('Principal')).not.toBeInTheDocument()
  })
})
