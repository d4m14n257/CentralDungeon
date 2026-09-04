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

// The shared accounts from pruebas-e1.md, rather than an invented one: master-1 is already
// Secondary of "La Cripta de Ondrak" and "Tumbas de Sal", with real applications - reusing them
// keeps the panel connected to the data everything has been tested against, instead of starting
// from an empty account.
const DEFAULT_PLAYER_ID = 'jugador-1'
const DEFAULT_MASTER_ID = 'master-1'
const DEFAULT_ADMIN_ID = 'admin-1'

/**
 * Only under `npm run dev` (import.meta.env.DEV, substituted at build time and dropped from the
 * production bundle) - it replaces the console `fetch()` calls of pruebas-e1.md with buttons. It
 * depends on the backend running with the `test` profile; without it, test-login answers 404 and the
 * global error toast (config/query.ts) says so. New roles (Admin, Owner) are added here once
 * test-login supports them - the shape is not anticipated yet.
 */
export function DevPanel() {
  if (!import.meta.env.DEV) {
    return null
  }
  return <DevPanelContent />
}

function DevPanelContent() {
  const { t } = useTranslation('dev')
  // enabled: isAuthenticated - the panel lives on /login too (for the quick sign-in right there),
  // and without this useMe() fires a 401 in a loop: the refresh fails, client.ts sends the browser to
  // /login with a full reload, which mounts the panel again and fires the same query
  // (it was breaking discord-login.spec.ts).
  const { isAuthenticated } = useAuth()
  const { data: me } = useMe(isAuthenticated)
  const [playerDiscordId, setPlayerDiscordId] = useState(DEFAULT_PLAYER_ID)
  const [masterDiscordId, setMasterDiscordId] = useState(DEFAULT_MASTER_ID)
  const [adminDiscordId, setAdminDiscordId] = useState(DEFAULT_ADMIN_ID)
  const [isLoggingIn, setIsLoggingIn] = useState(false)
  const createTable = useCreateTable()

  async function loginAsPlayerOrMaster(asMaster: boolean) {
    const discordId = asMaster ? masterDiscordId : playerDiscordId
    setIsLoggingIn(true)
    try {
      await testLoginAndReload(discordId.trim() || (asMaster ? DEFAULT_MASTER_ID : DEFAULT_PLAYER_ID), asMaster)
    } catch {
      toast.error(t('loginFailed'))
      setIsLoggingIn(false)
    }
  }

  async function loginAsAdmin() {
    setIsLoggingIn(true)
    try {
      await testLoginAndReload(adminDiscordId.trim() || DEFAULT_ADMIN_ID, false, true)
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
          <p className="text-fg-subtle text-xs">{me ? t('loggedInAs', { name: me.name ?? me.id }) : t('notLoggedIn')}</p>
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
              <Button size="sm" variant="secondary" disabled={isLoggingIn} onClick={() => void loginAsPlayerOrMaster(false)}>
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
              <Button size="sm" variant="secondary" disabled={isLoggingIn} onClick={() => void loginAsPlayerOrMaster(true)}>
                {t('loginAsMaster')}
              </Button>
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="dev-panel-admin-id">{t('adminDiscordIdLabel')}</Label>
            <div className="flex gap-2">
              <Input
                id="dev-panel-admin-id"
                value={adminDiscordId}
                onChange={(event) => setAdminDiscordId(event.target.value)}
                className="flex-1"
              />
              <Button size="sm" variant="secondary" disabled={isLoggingIn} onClick={() => void loginAsAdmin()}>
                {t('loginAsAdmin')}
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
