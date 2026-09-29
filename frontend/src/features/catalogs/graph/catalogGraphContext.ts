import { createContext, use } from 'react'

import type { CatalogKind } from '../types'

/** What every node of one catalog canvas shares, and cannot receive through React Flow's `data`. */
export interface CatalogGraphContextValue {
  /** Which catalog the canvas draws. */
  kind: CatalogKind
  /** Called with a value that has just become a group of its own, so the canvas draws it. */
  onBecameGroup: (id: string) => void
}

/**
 * The channel between the catalog canvas and its nodes (#275).
 *
 * React Flow renders node types by name, from a map registered once, so a node cannot be handed
 * props by the component that draws the canvas. What is the same for every node - the catalog, the
 * callback - travels here; what differs per node travels in its `data`. A subtree Context, not a
 * store: it is scoped to one canvas (#105).
 */
export const CatalogGraphContext = createContext<CatalogGraphContextValue | null>(null)

/**
 * Reads the canvas a node is drawn in.
 *
 * @returns the canvas's shared values
 * @throws when a catalog node is rendered outside a catalog canvas - a wiring mistake, not a state
 */
export function useCatalogGraph(): CatalogGraphContextValue {
  const context = use(CatalogGraphContext)
  if (!context) {
    throw new Error('useCatalogGraph must be used within a CatalogGraph')
  }
  return context
}
