import { create } from 'zustand'
import { persist } from 'zustand/middleware'

/**
 * Which section of the app the navigation is showing: player, master, admin or owner.
 *
 * **UI organisation, not authorization** (#103). Being "in the Admin context" allows nothing: the
 * backend decides endpoint by endpoint, and a forced route without the role gets a 403 and paints
 * `ForbiddenState`.
 */
export type AppContext = 'player' | 'master' | 'admin'

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
