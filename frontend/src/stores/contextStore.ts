import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export type AppContext = 'player' | 'master'

interface ContextState {
  activeContext: AppContext
  setActiveContext: (context: AppContext) => void
}

/** Which role the ContextSwitcher currently shows (frontend-diseno.md 2) - UI organization only, never authorization (#103). */
export const useContextStore = create<ContextState>()(
  persist(
    (set) => ({
      activeContext: 'player',
      setActiveContext: (context) => {
        set({ activeContext: context })
      },
    }),
    { name: 'centraldungeon-context' },
  ),
)
