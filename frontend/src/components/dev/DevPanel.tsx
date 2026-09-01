import { FlaskConical } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { useCreateTable } from '@/features/tables'
import { useMe } from '@/features/users'
import { useAuth } from '@/providers/AuthProvider'

import { testLoginAndReload } from './devApi'

// Las cuentas compartidas de pruebas-e1.md, no una inventada: master-1 ya es Secondary de "La
// Cripta de Ondrak" y "Tumbas de Sal", con postulaciones reales - reusarlas mantiene el panel
// conectado a los datos con los que ya se viene probando, en vez de arrancar una cuenta vacía.
const DEFAULT_PLAYER_ID = 'jugador-1'
const DEFAULT_MASTER_ID = 'master-1'

/**
 * Solo en `npm run dev` (import.meta.env.DEV, reemplazado en build y eliminado del bundle de
 * producción) - reemplaza los fetch() de consola de pruebas-e1.md por botones. Depende de que el
 * backend corra con el perfil `test`; si no, test-login responde 404 y el toast de error global
 * (config/query.ts) lo avisa. Roles nuevos (Admin, Owner) se agregan acá cuando test-login los
 * soporte - no se anticipa la forma todavía.
 */
export function DevPanel() {
  if (!import.meta.env.DEV) {
    return null
  }
  return <DevPanelContent />
}

function DevPanelContent() {
  const { t } = useTranslation('dev')
  // enabled: isAuthenticated - el panel también vive en /login (para el login rápido ahí mismo),
  // y sin esto useMe() dispara un 401 en bucle: falla el refresh y client.ts manda a /login con un
  // reload completo, que vuelve a montar el panel y a disparar la misma consulta (rompía discord-login.spec.ts).
  const { isAuthenticated } = useAuth()
  const { data: me } = useMe(isAuthenticated)
  const [playerDiscordId, setPlayerDiscordId] = useState(DEFAULT_PLAYER_ID)
  const [masterDiscordId, setMasterDiscordId] = useState(DEFAULT_MASTER_ID)
  const [isLoggingIn, setIsLoggingIn] = useState(false)
  const createTable = useCreateTable()

  async function loginAs(asMaster: boolean) {
    const discordId = asMaster ? masterDiscordId : playerDiscordId
    setIsLoggingIn(true)
    try {
      await testLoginAndReload(discordId.trim() || (asMaster ? DEFAULT_MASTER_ID : DEFAULT_PLAYER_ID), asMaster)
    } catch {
      toast.error(t('loginFailed'))
      setIsLoggingIn(false)
    }
  }

  function createTestTable() {
    const name = `${t('testTableName')} ${new Date().toLocaleTimeString('es')}`
    createTable.mutate(
      { name, maxPlayers: 4 },
      {
        onSuccess: (table) => {
          toast.success(t('testTableCreated', { name: table.name }))
        },
      },
    )
  }

  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          size="icon"
          className="border-brand-fg text-brand-fg fixed right-4 bottom-4 z-40 rounded-full shadow-sm"
          aria-label={t('title')}
        >
          <FlaskConical className="size-4" />
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" side="top" className="w-80 space-y-4">
        <div>
          <p className="font-serif text-sm font-semibold">{t('title')}</p>
          <p className="text-fg-subtle text-xs">
            {me ? t('loggedInAs', { name: me.name ?? me.id }) : t('notLoggedIn')}
          </p>
        </div>

        <div className="space-y-2">
          <div className="space-y-1.5">
            <Label htmlFor="dev-panel-player-id">{t('playerDiscordIdLabel')}</Label>
            <div className="flex gap-2">
              <Input
                id="dev-panel-player-id"
                value={playerDiscordId}
                onChange={(event) => setPlayerDiscordId(event.target.value)}
                className="flex-1"
              />
              <Button size="sm" variant="secondary" disabled={isLoggingIn} onClick={() => void loginAs(false)}>
                {t('loginAsPlayer')}
              </Button>
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="dev-panel-master-id">{t('masterDiscordIdLabel')}</Label>
            <div className="flex gap-2">
              <Input
                id="dev-panel-master-id"
                value={masterDiscordId}
                onChange={(event) => setMasterDiscordId(event.target.value)}
                className="flex-1"
              />
              <Button size="sm" variant="secondary" disabled={isLoggingIn} onClick={() => void loginAs(true)}>
                {t('loginAsMaster')}
              </Button>
            </div>
          </div>
        </div>

        <div className="border-border border-t pt-3">
          <Button size="sm" variant="outline" className="w-full" disabled={createTable.isPending} onClick={createTestTable}>
            {t('createTestTable')}
          </Button>
        </div>
      </PopoverContent>
    </Popover>
  )
}
